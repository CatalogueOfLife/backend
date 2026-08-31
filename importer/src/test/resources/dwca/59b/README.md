The second version of `dwca/59`: `Carex nigra` (and with it the implicit Cyperaceae and Carex) is gone,
`Aster novi-belgii` was added under an existing genus, `Xylaria hypoxylon` under a brand new family and
genus, and every taxonID was renumbered.

The implicit higher taxa are created while iterating the source usages in the store's hash order, so a
changed set of source ids reorders them and used to give them all new identifiers.
See https://github.com/CatalogueOfLife/backend/issues/1189
