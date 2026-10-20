# Observations are append-only and retracted, not edited

A wrong observation stays in the ledger. A retraction is a new row that points at it and records why. Knowledge state is recomputed from observations that have no retraction.

Editing the old row would erase the evidence the app is supposed to keep. Deleting it would make a later rebuild disagree with what the teacher once stored. A new success is a new observation, so the mistake and the later success are both visible.
