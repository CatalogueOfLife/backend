# Test data sets

Each subfolder is one test data set that `TestDataRule` COPYs straight into postgres before a test runs.
`TestDataRules` (and `TestDataRule.TestData` for the sets in the dao module) names them and maps the
dataset keys. Sets also live in `dao/src/test/resources/test-data`; everything here applies to those too.

**These files are maintained by hand.** There used to be a `TestDataGenerator` that rebuilt some of them
by importing real archives, but the files had long since been edited directly and drifted from what it
produced - different columns, different dataset keys - so it was removed rather than left as a trap.
Bulk changes are done with a script or SQL over the CSV files, the way the name-parser v4 NAMETYPE
migration was done in `181fd55f6`. Some archives under `importer/src/test/resources` note which set they
originally seeded, but they are no longer in sync with it.

## Layout

| File | Table |
|---|---|
| `<table>.csv` | a global table, e.g. `dataset.csv`, `sector.csv`, `names_index.csv`, `name_match.csv` |
| `<table>_<datasetKey>.csv` | a dataset partitioned table, e.g. `name_118.csv`, `name_usage_118.csv` |

The first line of every file is a header of postgres column names, and only those columns are copied -
so a set does not need to carry every column of a table, and different sets legitimately carry different
subsets. Missing files are skipped, so a set only needs the tables its tests touch.

## Pitfalls

- **All `name_*.csv` files in one set must share the same column layout.** `TestDataRule.readNameColumns`
  reads the header of a *single* one of them and reuses those column indices for every other name file in
  the set. Add a column to one file only and the rest are silently misread. The same holds for
  `name_usage_*.csv` via `readNameUsageColumns`.
- **`dataset.csv` must keep the dataset key as its first column** - `readDatasetKeys` parses it positionally.
- Postgres array columns are written in curly brace form (`{}`, `{Döring}`), an empty field means NULL.
- Enum columns hold the postgres enum labels. When an enum changes, these files have to be migrated with
  it, and `dbschema.md` is where that migration belongs.

`TestDataLoadTest` in the importer module loads every set and is the guard that a hand edit still parses
and still matches the schema. Run it after touching anything here.
