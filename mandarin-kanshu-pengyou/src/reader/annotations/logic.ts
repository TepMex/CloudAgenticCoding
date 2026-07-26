import type { AnnotationRecord } from "../../shared/domain";

/** Logical annotations are never written into EPUB HTML permanently. */
export function annotationsForSpine(
  annotations: AnnotationRecord[],
  spineItemId: string,
): AnnotationRecord[] {
  return annotations.filter((a) => a.location.spineItemId === spineItemId);
}

/** Overlapping ranges can share a simplified visual marker in the UI. */
export function mergeOverlappingMarkers(
  annotations: AnnotationRecord[],
): { location: AnnotationRecord["location"]; ids: string[] }[] {
  return annotations.map((a) => ({ location: a.location, ids: [a.id] }));
}
