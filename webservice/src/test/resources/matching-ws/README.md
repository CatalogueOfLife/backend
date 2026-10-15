# matching-ws test index

The `index/` files are copied from the tests of GBIF's matching service,
https://github.com/gbif/matching-ws (`src/test/resources/index`), licensed under the Apache License 2.0.

- `nubNN.json` are verbose GBIF v1 species match responses, keyed by GBIF backbone ids.
- `matchNN.json` are v2 responses from a COL based index, keyed by COL ids.

`MatchingWsIndex` turns every usage, classification entry and alternative in them into one in-memory
ChecklistBank matcher store, the same way matching-ws builds its test index, so the ported matching-ws test
cases can run against the ChecklistBank matcher behind the GBIF compatible `/match/gbif`.
