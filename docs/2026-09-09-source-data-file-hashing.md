# Hashing the data files of a source archive

Date: 2026-09-09
Status: implemented, not yet deployed

## Problem

[CatalogueOfLife/data#1694](https://github.com/CatalogueOfLife/data/issues/1694) asks that Species Files and
WoRMS only yield a new CLB version when the data actually changed.

Today `ImportJob.hasMD5Changed` MD5s the whole downloaded archive and compares it against the last
*successful* attempt (`DatasetImportMapper.getMD5`). Equal means the import body is skipped and the
`dataset_import` row deleted again - see
[2026-08-27-unchanged-import-job-history.md](2026-08-27-unchanged-import-job-history.md). That check never
fires for the Species Files, because their `metadata.yaml` is rewritten on every export, so the archive
always differs and every month looks like a new version.

Two fixes were proposed in the issue:

1. Report added/removed/changed record counts per import, from the diff tool.
2. Strip `metadata.yml` from the zip and checksum the rest.

(1) is out: the diff tool runs in memory, caps its report at 25,000 records, is far too expensive to run on
every import, and only covers names - not taxonomy, references, associated data or nomenclatural relations.

(2) is the right idea in the wrong place. A zip also carries entry order, per-entry timestamps and the
compression level, all of which change when the exporter is re-run, and an archive may hold any number of
extra files. The proposal considered here is therefore to hash **the data files the importer actually
detected**, after extraction.

## What the archives actually show

Measured 2026-09-09 against prod, using the per-entry CRC-32 in each archive's zip central directory
(`GET /dataset/{key}/archive?attempt={n}`, then `unzip -v`), on Species Files whose record counts did not
move at all between the two imports:

| dataset | attempts | data files differing | verdict |
|---|---|---|---|
| SF Mantodea 1062 | 41 → 42 | `References.tsv`, 43 rows | real - citations expanded, per-row `modified` restamped |
| SF Mantodea 1062 | 42 → 43 | `Name.tsv` 15 rows ("Linné" → "Linnaeus"), `References.tsv` 30, `Taxon.tsv` 2 | real |
| SF Mantodea 1062 | 43 → 44 | `Taxon.tsv`, 4 rows | **noise** |
| SF Mantodea 1062 | 44 → 45 | `Taxon.tsv`, 3 rows | **noise** |
| SF Grylloblattodea 1170 | 22 → 23 | `Taxon.tsv`, all 1,189 rows | **noise** |
| SF Coleorrhyncha 1192 | 19 → 20 | `Taxon.tsv`, all 170 rows | **noise** |
| SF Zoraptera 1167 | 19 → 20 | `Taxon.tsv`, all 84 rows | **noise** |

`metadata.yaml` differed in all seven. Every other file was byte identical - in 44 → 45, nine of the ten
files were untouched.

The two kinds of noise are worth naming, because they bound what any hash can do.

**Non-deterministic multi-value cells.** In Mantodea 43 → 44 and 44 → 45 the only difference is the order of
IDs inside one comma-separated `referenceID` cell:

```
158169,158400,158316,158324   ->   158324,158169,158400,158316
```

Same row, same set of references. The exporter aggregates without an `ORDER BY`. The importer splits that
very field on comma itself (`ColdpInterpreter.COMMA_SPLITTER`).

**The exporter changing.** In 1170, 1192 and 1167 *every* row of `Taxon.tsv` changed and the file doubled in
size, with an unchanged 18 column header: the export started emitting `extinct` and a `link` permalink for
every taxon.

```
924553|1304867|1031939||||||||||||||2023-08-26T18:07:43Z|
924553|1304867|1031939||||||||0||||https://grylloblattodea.speciesfile.org/otus/924553/overview||2023-08-26T18:07:43Z|
```

Nothing taxonomic changed; the exporter got better.

**So a hash proves sameness, never change.** Equal hash is a reliable "nothing happened". A differing hash
says only that some bytes moved, and on these seven transitions it would have reported "data changed" seven
times out of seven, while only two were real content edits. Excluding `metadata.yaml` removes the most
common false positive and nothing else.

The WoRMS half of the issue is a different problem: WoRMS Polychaeta (1090) grew on every one of attempts
83-87 (106,867 → 107,869 verbatim records). That source really does change monthly.

## Decisions

### The archive MD5 fast path stays exactly as it is

It is what keeps the no-op imports cheap - ~390 import jobs/day on dev, 93% of them no-ops. An identical
archive is still detected before decompression, still skips everything, still deletes its metrics row. The
data hash is computed only on the branch that already decompresses and imports, so the common case gains no
work at all.

### Imports still run, the hash is only recorded

When the archive changed but the data files did not, the import proceeds as today. It has to: the result of
an import depends on more than the source bytes - interpretation code, dataset settings, the names index
state, and features like `ADD_IDENTIFIERS_FROM` all change what the same input produces. What is new is only
that we record *when the data last changed*, next to when we last imported.

Skipping the data and applying metadata only was considered and rejected for the same reason: it would
silently withhold new interpretation from a source, and it needs a second, parallel import path.

### Hash the extracted, detected data files - not the zip

Hashing after extraction is strictly better than "the zip minus metadata.yml":

- Zip metadata (entry order, timestamps, compression level) is out of scope by construction.
- `CompressionUtil.isHiddenFile` already drops every dot-prefixed path segment during extraction, so
  `.DS_Store` and `__MACOSX/._*` never reach the scratch dir. The hidden-file worry applies to the zip, not
  to the unpacked tree.
- Unrecognised extra files never enter the reader's schema map, so they cost nothing and are excluded for
  free.

### What counts as a data file

The reader already knows. `CsvReaderFactory.open(folder)` builds a reader over the unpacked folder without
running an import, and `CsvReader.schemas()` yields a `Schema` per row type with its `List<Path> files`.
On top of that:

| format | also included | excluded |
|---|---|---|
| COLDP | `reference.bib`, `reference.json`, `reference.jsonl`, `treatments/`, `default.yaml` | `metadata.yaml`/`.yml`/`.json`, `eml.xml`, `logo.png` |
| DwC-A | `meta.xml` | the `archive/@metadata` file, `eml.xml` |
| ACEF | - | (metadata is a data row, `SourceDatabase`) |
| TEXT_TREE | the tree file from `TxtTreeInserter.findReadable`, `reference.bib` | metadata files |

`default.yaml` and `meta.xml` are *in* the hash: both change how the same rows are interpreted. Metadata
filenames come from `MetadataFactory`, which already owns that list.

### Aggregation and storage

Digest each file, then digest the lines `<path relative to the source dir>\t<hex>` sorted by path. That is
independent of entry order and of `CompressionUtil.removeRootDirectories` flattening, and sensitive to
renames, which is correct.

- `dataset_import.data_md5 TEXT`, mirroring the existing `md5` column, with a `getDataMD5(datasetKey,
  attempt)` beside `getMD5`.
- `dataset.data_attempt INTEGER` - the attempt whose data last differed. Advanced by `PgImport` next to
  `DatasetMapper.updateLastImport`, and only when the hash differs, so a failed import never moves it.
- Computed in `ImportJob.prepareSourceData`, in the `doImport` branch after `decompressFile` and format
  detection.

There is no backfill: the first import of every source after the deploy has no previous `data_md5` and reads
as changed once.

`ChecksumUtils` should get a bigger buffer in the same change - it reads a `FileInputStream` 1 KiB at a
time, which is one read syscall per KiB, about a million per GB.

## Performance

Measured on an M4 Pro, and against real prod archives:

- `openssl speed`: MD5 918 MB/s, SHA-256 3.37 GB/s. SHA-256 only wins where the CPU accelerates it, so MD5
  stays the default - it also matches the existing column.
- MD5 over a real 70 MB COLDP `Taxon.tsv`: 737 MB/s. `awk -F'\t'` splitting fields on the same file:
  68 MB/s - the anchor for anything that has to touch every cell.
- Archive sizes: ITIS 37.8 MB zip → 377 MB uncompressed in 7 files; World Plants 227 MB zip (~2 GB), PoWo
  130 MB, Species Fungorum 112 MB, IRMNG 93 MB, WCVP 88 MB, SF Psocodea 1.5 MB, SF Mantodea 0.22 MB
  (1.3 MB in 10 files).
- Import wall times: ITIS 46 min, World Plants 74, PoWo 89, WCVP 76, IRMNG 43.

At a conservative 500 MB/s for MD5 on the prod VMs, and 70 MB/s for a pass that parses every cell:

| source | uncompressed | byte hash | row fingerprint, own pass | row fingerprint, piggybacked | import |
|---|---:|---:|---:|---:|---:|
| SF Mantodea | 1.3 MB | 0.003 s | 0.02 s | ~0 s | ~30 s |
| SF Psocodea | ~15 MB | 0.03 s | 0.2 s | 0.02 s | ~2 min |
| ITIS | 377 MB | 0.8 s | 5.4 s | 0.5 s | 46 min |
| World Plants | ~2 GB | 4 s | 29 s | 3 s | 74 min |

Byte hashing costs 0.03-0.09% of import wall time. Memory is constant, disk unchanged, and the files were
just written by the decompressor so the read is normally page-cache warm; even a cold 200 MB/s read of the
2 GB case is 10 s, under 0.25% of that import.

The number of files barely matters - roughly 20 µs per open/stat/close, so ten files cost 0.2 ms and ten
thousand cost 0.2 s. The exception is a COLDP `treatments/` folder holding tens of thousands of small files,
where per-file overhead outweighs per-byte cost.

## Rejected alternative: an order-insensitive row fingerprint

Hash the raw cell values of each row, combine them so row order does not matter, accumulate per row type.
That survives row reordering, column reordering and requoting.

Its CPU cost is affordable: 0.2-0.7% of import wall time as its own pass, and under 0.1% if it piggybacks on
the parse the normalizer already does. That is not why it is rejected.

- It would not have caught either kind of noise measured above. The multi-value case needs the *cell*
  normalised - split on the delimiter and sorted - and the exporter case is a genuine value change that no
  fingerprint can excuse.
- To normalise those cells it would need to know which COLDP columns are multi-valued, and nothing the
  reader can see declares that. Only DwC-A `delimitedBy` reaches `Schema.Field.delimiter`; for COLDP the
  knowledge lives in the interpreter. It would need a multi-valued-term vocabulary on `ColdpTerm` first.
- Discarding row order is wrong for TEXT_TREE, where the order *is* the hierarchy, so that format would
  have to stay on byte hashing regardless.

Worth revisiting only if the byte hash proves too noisy after the upstream fix below.

## The cheaper fix, upstream

Making the Species File export deterministic - an `ORDER BY` on the aggregated ID lists - costs one line and
removes a whole class of false positives that no amount of hashing on our side can. It is worth more than
this feature is, and it is a precondition for this feature being useful on those sources.

## What this delivers

A trustworthy negative and a date. "The data files are byte identical to the last import" becomes a fact we
record, and `dataset.data_attempt` answers "when did this source last actually ship data". It does not, on
its own, make the monthly version reflect the data - the evidence above says it cannot.

## Outcome

Implemented as described. `DataFiles.list(folder, format)` enumerates the data files,
`ChecksumUtils.getMD5Checksum(folder, files)` aggregates them, `ImportJob.hashDataFiles` records the
result on `dataset_import.data_md5`, and a successful `PgImport` moves `dataset.data_attempt` only when the checksum
differed from the last successful attempt.

Four things the spec above did not settle:

- **ACEF metadata is a data row.** ACEF has no metadata file - `AcefInserter` reads it from the
  `SourceDatabase` row type. That schema is therefore excluded from the file list, so the rule "metadata is
  never part of the checksum" holds for all four formats rather than three.
- **A checksum that cannot be taken is not a claim of sameness.** If listing or reading the data files
  throws, the import logs a warning, leaves `data_md5` null and treats the data as changed. The alternative
  - failing the import over an accessory checksum - would be worse, and silently reporting "unchanged"
  would be a lie.
- **The archive MD5 fast path was not touched at all.** An identical archive still never gets extracted, so
  the ~93% of scheduled imports that find nothing new cost exactly what they cost before.
- **`ChecksumUtils` read through a 1 KiB buffer**, one syscall per KiB. Raised to 64 KiB; the existing test
  pins the checksum value, so the change is proven not to alter any result.

Releases pass a null data attempt: they have no source archive, so there is nothing to compare and the
column stays null rather than implying a comparison happened.

Deliberately left out:

- **Exposing it in the UI.** `Dataset.dataAttempt` and `DatasetImport.dataMd5` are in the API; the
  checklistbank metadata page still shows only "last imported". Separate repo, separate PR.
- **Backfill.** Not possible without re-extracting every stored archive, and the first import of each
  source after the deploy necessarily reads as changed.
