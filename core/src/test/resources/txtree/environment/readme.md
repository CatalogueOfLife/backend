A merge sector enriches existing taxa with the taxon properties they lack, but never overrides one they have.

| Taxon | Project | Source | Result |
|---|---|---|---|
| `Abra` | no environment | marine | marine, from the source |
| `Abra alba` | nothing | marine & brackish, Pliocene-Holocene | marine & brackish, Pliocene-Holocene, both from the source |
| `Abra nitida` | Miocene | marine, Pleistocene | marine from the source, keeps its own Miocene |
| `Abra prismatica` | freshwater | marine | keeps freshwater |
| `Abra segmentum` | - | marine | created by the merge, marine |
| `Abra tenuis` | not extinct | † extinct | stays not extinct, flagged `POTENTIALLY_EXTINCT` for review |

Environments and temporal range taken over are recorded as `ENVIRONMENT` and `TEMPORAL_RANGE` secondary sources.
The extinct flag is never merged. The merge only raises an issue instead, so such cases can be found, quantified and
reviewed.
None of this shows in `expected.txtree`, which prints no taxon properties, so `environmentValidate()` asserts it.
