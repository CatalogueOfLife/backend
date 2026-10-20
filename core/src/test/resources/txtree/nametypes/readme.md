A sector that only admits scientific names drops every name of another type, whether the parser made sense of it or not:

- placeholders: `Mollusca incertae sedis` parsed and anchored on Mollusca, `[unassigned] Cladobranchia` unparsable
- informal names: the phrase name `Abra sp. Kent (A.B. 1234)` and `Abra cf. alba`, both parsed
- other names: the unparsable viruses `Viruses` and `Tobacco mosaic virus`

The children of a dropped placeholder attach to its parent. `Tobamovirus tabaci` is a virus too, but a scientific
binomial, so the type filter keeps it.
