"""IDS tree parsing and structural simplification comparison."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

# Ideographic Description Characters and their arity.
IDS_OPERATORS: dict[str, int] = {
    "\u2ff0": 2,  # ⿰ left-right
    "\u2ff1": 2,  # ⿱ above-below
    "\u2ff2": 3,  # ⿲ left-middle-right
    "\u2ff3": 3,  # ⿳ above-middle-below
    "\u2ff4": 2,  # ⿴ full surround
    "\u2ff5": 2,  # ⿵ surround from above
    "\u2ff6": 2,  # ⿶ surround from below
    "\u2ff7": 2,  # ⿷ surround from left
    "\u2ff8": 2,  # ⿸ surround from upper left
    "\u2ff9": 2,  # ⿹ surround from upper right
    "\u2ffa": 2,  # ⿺ surround from lower left
    "\u2ffb": 2,  # ⿻ overlaid
}

STRUCTURE_LABELS = {
    "\u2ff0": "left-right",
    "\u2ff1": "top-bottom",
    "\u2ff2": "left-middle-right",
    "\u2ff3": "top-middle-bottom",
    "\u2ff4": "full-surround",
    "\u2ff5": "surround-from-above",
    "\u2ff6": "surround-from-below",
    "\u2ff7": "surround-from-left",
    "\u2ff8": "surround-from-upper-left",
    "\u2ff9": "surround-from-upper-right",
    "\u2ffa": "surround-from-lower-left",
    "\u2ffb": "overlaid",
}


@dataclass
class IdsNode:
    value: str
    children: list[IdsNode] = field(default_factory=list)

    @property
    def is_operator(self) -> bool:
        return self.value in IDS_OPERATORS

    def leaf_string(self) -> str:
        if not self.children:
            return self.value
        return self.value + "".join(c.leaf_string() for c in self.children)


def parse_ids(ids: str | None) -> IdsNode | None:
    """Parse an IDS string into a tree. Returns None if unparsable or marked unknown."""
    if not ids:
        return None
    text = ids.strip()
    if not text or "？" in text or "?" in text:
        return None
    # Normalize to code points
    chars = list(text)
    index = 0

    def parse_node() -> IdsNode | None:
        nonlocal index
        if index >= len(chars):
            return None
        ch = chars[index]
        index += 1
        arity = IDS_OPERATORS.get(ch)
        if arity is None:
            return IdsNode(value=ch)
        children: list[IdsNode] = []
        for _ in range(arity):
            child = parse_node()
            if child is None:
                return None
            children.append(child)
        return IdsNode(value=ch, children=children)

    root = parse_node()
    if root is None or index != len(chars):
        return None
    return root


@dataclass
class ComponentChange:
    traditional_component: str
    simplified_component: str
    path: list[int]
    change_type: str = "replacement"

    def to_json_dict(self) -> dict[str, Any]:
        return {
            "traditionalComponent": self.traditional_component,
            "simplifiedComponent": self.simplified_component,
            "path": self.path,
            "changeType": self.change_type,
        }


@dataclass
class StructuralDiffResult:
    classification: str
    explanation: str
    changed_components: list[ComponentChange]
    confidence: float
    evidence_type: str = "derived"


def compare_ids_trees(
    traditional_ids: str | None,
    simplified_ids: str | None,
    *,
    traditional_char: str,
    simplified_char: str,
    ambiguous_mapping: bool = False,
) -> StructuralDiffResult:
    """Compare IDS trees and produce a conservative structural classification."""
    if ambiguous_mapping:
        return StructuralDiffResult(
            classification="AMBIGUOUS_VARIANT_MAPPING",
            explanation=(
                f"{traditional_char} ↔ {simplified_char}\n"
                "Classification: ambiguous variant mapping.\n"
                "Evidence: source lists a one-to-many or context-dependent pair; "
                "no single structural explanation was selected."
            ),
            changed_components=[],
            confidence=0.4,
        )

    if traditional_char == simplified_char:
        return StructuralDiffResult(
            classification="UNCHANGED",
            explanation=(
                f"{traditional_char} → {simplified_char}\n"
                "No standard simplified/traditional difference found."
            ),
            changed_components=[],
            confidence=1.0,
        )

    t_tree = parse_ids(traditional_ids)
    s_tree = parse_ids(simplified_ids)

    if t_tree is None or s_tree is None:
        return StructuralDiffResult(
            classification="UNKNOWN",
            explanation=(
                f"{traditional_char} → {simplified_char}\n"
                "Classification: unknown.\n"
                "No reliable IDS decomposition was available for structural comparison.\n"
                "Evidence: derived structural comparison; variant pair is source-backed."
            ),
            changed_components=[],
            confidence=0.3,
        )

    if _trees_equal(t_tree, s_tree):
        return StructuralDiffResult(
            classification="UNCHANGED",
            explanation=(
                f"{traditional_char} → {simplified_char}\n"
                "No standard simplified/traditional difference found."
            ),
            changed_components=[],
            confidence=0.9,
        )

    if t_tree.is_operator and s_tree.is_operator and t_tree.value == s_tree.value:
        changes = _diff_same_structure(t_tree, s_tree, path=[])
        if changes is None:
            return StructuralDiffResult(
                classification="STRUCTURE_CHANGED_OR_WHOLE_CHARACTER_REPLACEMENT",
                explanation=(
                    f"{traditional_char} → {simplified_char}\n"
                    "Classification: structure changed or whole-character replacement.\n"
                    "The simplified and traditional decomposition trees do not have a "
                    "reliable component-for-component correspondence.\n"
                    "Evidence: derived structural comparison; variant pair is source-backed."
                ),
                changed_components=[],
                confidence=0.55,
            )
        structure = STRUCTURE_LABELS.get(t_tree.value, "same root structure")
        if len(changes) == 1:
            c = changes[0]
            return StructuralDiffResult(
                classification="SINGLE_COMPONENT_REPLACEMENT",
                explanation=(
                    f"{traditional_char} → {simplified_char}\n"
                    "Classification: single component replacement.\n"
                    f"Structural change: {c.traditional_component} was replaced by "
                    f"{c.simplified_component}; the {structure} structure was retained.\n"
                    "Evidence: derived structural comparison; variant pair is source-backed."
                ),
                changed_components=changes,
                confidence=0.75,
            )
        return StructuralDiffResult(
            classification="MULTIPLE_COMPONENT_REPLACEMENTS",
            explanation=(
                f"{traditional_char} → {simplified_char}\n"
                "Classification: multiple component replacements.\n"
                f"Structural change: multiple subcomponents changed while the overall "
                f"{structure} structure was retained.\n"
                "Evidence: derived structural comparison; variant pair is source-backed."
            ),
            changed_components=changes,
            confidence=0.7,
        )

    return StructuralDiffResult(
        classification="STRUCTURE_CHANGED_OR_WHOLE_CHARACTER_REPLACEMENT",
        explanation=(
            f"{traditional_char} → {simplified_char}\n"
            "Classification: structure changed or whole-character replacement.\n"
            "The simplified and traditional decomposition trees do not have a "
            "reliable component-for-component correspondence.\n"
            "Evidence: derived structural comparison; variant pair is source-backed."
        ),
        changed_components=[],
        confidence=0.6,
    )


def _trees_equal(a: IdsNode, b: IdsNode) -> bool:
    if a.value != b.value or len(a.children) != len(b.children):
        return False
    return all(_trees_equal(x, y) for x, y in zip(a.children, b.children))


def _diff_same_structure(
    t: IdsNode,
    s: IdsNode,
    path: list[int],
) -> list[ComponentChange] | None:
    """Diff two trees with the same operators along aligned paths.

    Returns None when children cannot be aligned (arity mismatch deeper down).
    """
    if not t.is_operator and not s.is_operator:
        if t.value == s.value:
            return []
        return [
            ComponentChange(
                traditional_component=t.value,
                simplified_component=s.value,
                path=list(path),
            )
        ]

    if t.is_operator != s.is_operator or t.value != s.value:
        return None
    if len(t.children) != len(s.children):
        return None

    changes: list[ComponentChange] = []
    for i, (tc, sc) in enumerate(zip(t.children, s.children)):
        child_path = path + [i]
        if _trees_equal(tc, sc):
            continue
        # If both children are leaves or share structure, recurse; else treat as one subtree swap.
        if (tc.is_operator or sc.is_operator) and (
            tc.is_operator == sc.is_operator and tc.value == sc.value
        ):
            nested = _diff_same_structure(tc, sc, child_path)
            if nested is None:
                changes.append(
                    ComponentChange(
                        traditional_component=tc.leaf_string(),
                        simplified_component=sc.leaf_string(),
                        path=child_path,
                    )
                )
            else:
                changes.extend(nested)
        elif not tc.is_operator and not sc.is_operator:
            changes.append(
                ComponentChange(
                    traditional_component=tc.value,
                    simplified_component=sc.value,
                    path=child_path,
                )
            )
        else:
            # Entire subtree replacement under same parent operator.
            changes.append(
                ComponentChange(
                    traditional_component=tc.leaf_string(),
                    simplified_component=sc.leaf_string(),
                    path=child_path,
                )
            )
    return changes
