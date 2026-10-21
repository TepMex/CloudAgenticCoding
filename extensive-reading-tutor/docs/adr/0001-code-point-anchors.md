# Anchors use Unicode code points inside a block

Book position is a half-open range of Unicode code points in one extracted paragraph, plus the exact quote and its SHA-256. Page numbers, view positions, and Readium locators are not the stored position.

Kotlin's `String` indexes are UTF-16, so a supplementary character would split if we stored those indexes. Code points survive CJK text and characters outside the basic plane. The quote is checked against the block on write, so a later reader can see that the range still matches the original slice. Grapheme and sentence boundaries are used only when a paragraph must be cut; the stored numbers remain code points.
