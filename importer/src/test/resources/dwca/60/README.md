WoRMS style taxonomic status values that carry a nomenclatural rather than a taxonomic statement,
see https://github.com/CatalogueOfLife/backend/issues/1571

One row per branch of `InterpreterBase.interpretUsage`:

| id | what it pins down |
|---|---|
| 3 | the accepted taxon the synonyms below point at |
| 4 | `unavailable name` **with** a foreign acceptedNameUsageID - a bare name state upgraded to a synonym |
| 5 | `unavailable name` whose acceptedNameUsageID points at itself - stays a bare name, no issue |
| 6 | `interim unpublished` with no acceptedNameUsageID at all - stays a bare name, no issue |
| 7 | `unavailable name` pointing at a non existing id - upgraded, then demoted back to a bare name by `Normalizer.removeOrphanSynonyms` |
| 8 | `nomen nudum` - a plain synonym that also derives NomStatus NOT_ESTABLISHED |
| 9 | `valid` - a false friend. It is a taxonomic verdict, so it must NOT derive NomStatus ACCEPTABLE |
| 10 | an explicit dwc:nomenclaturalStatus always wins over the derived one |
| 11 | `superseded combination` - a homotypic synonym, so it gets a HOMOTYPIC name relation |
