# Knowledge state is a deterministic projection

`LearnerKnowledgeState` is rebuilt from the observation ledger by fixed rules. The app does not store a probability and does not treat one observation as mastery.

The statuses are `LEARNING`, `NEEDS_REVIEW`, `LIKELY_KNOWN`, and `STABLE`. `STABLE` needs three recent positive observations in at least two lessons. Hint-dependent and partial results do not count as those successes. Comprehension and active use are different skills, so they do not share a row. A retraction changes the projection on the next rebuild and leaves the ledger in place. No embeddings are required; the next lesson selects rows with SQL-shaped filters and a character budget.
