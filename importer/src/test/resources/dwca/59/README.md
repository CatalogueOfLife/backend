A DwC-A with a purely denormalised classification: every row is a species and every higher taxon
has to be created implicitly by the normalizer. `Aster` exists twice as a genus, under Asteraceae
and under Poaceae, so the two are only told apart by their parent.

Paired with `59b`, which is the same source after an edit, to test that the generated identifiers
of the implicit higher taxa survive a re-import. See https://github.com/CatalogueOfLife/backend/issues/1189
