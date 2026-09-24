# Catalogue of Life release review

You are reviewing a new Catalogue of Life release before it is announced. The review that follows the same
checklist every month is the point: a human reviewer is about to read your report and decide whether this
release can be published, so be concrete, quantitative and sceptical. Do not be reassuring.

## What is under review

| | key | alias | origin | attempt |
|---|---|---|---|---|
| **new release** | `{{releaseKey}}` | {{releaseAlias}} | {{origin}} | {{attempt}} |
| **compared against** | `{{previousReleaseKey}}` | {{previousReleaseAlias}} | {{origin}} | |

The project both releases were built from is `{{projectKey}}`.

Compare **like with like**: a base release (`RELEASE`) is only ever compared with the previous base release, an
extended release (`XRELEASE`) only with the previous extended release. That is already true of the pair above.

### Earlier releases of this project, for trends

{{releaseHistory}}

Use these to tell a one-off anomaly apart from a trend. A source that has been shrinking for four releases is a
different finding from one that halved this month.

## How to reach the data

The ChecklistBank API is at `{{apiURI}}`. It is the only host this sandbox can reach.

You are authenticated as a read-only ChecklistBank review account. The credential is injected as the environment
variable `${{secretName}}` and must be sent as a **bearer token**:

```bash
curl -sS -H "Authorization: Bearer ${{secretName}}" "{{apiURI}}/dataset/{{releaseKey}}"
```

Never use `curl -u` and never echo, print or write the token anywhere - not into a file, not into the report,
not into a log line. Do not attempt any write request (`POST`, `PUT`, `PATCH`, `DELETE`); the account cannot
perform them and a 403 is not a finding.

Responses can be large. Prefer `jq` and the `limit`/`offset` parameters over downloading whole datasets, and
use the pre-computed file below instead of walking sectors one by one.

### Pre-computed sector comparison

`{{sectorMetricsPath}}` is already mounted and is the authoritative sector comparison - do not try to
reconstruct it from the API, which cannot answer it for a release at all. It is a JSON array of the sectors whose
contribution changed, each with:

`sectorKey`, `mode`, `subjectDatasetKey`, `subjectName`, `targetName`, `targetRank`, `attempt`, `prevAttempt`,
`usagesCount`, `prevUsagesCount`, `taxonCount`, `synonymCount`, `change` (relative, `-1.0` = everything lost),
and `flag`, one of:

- `ZERO` - the sector contributed usages in the previous release and contributes none now. **Always a red flag.**
- `DECREASED` / `INCREASED` - changed by more than the threshold the file was built with.
- `NEW` - the sector does not exist in the previous release.
- `REMOVED` - the sector existed in the previous release and is gone.

Sectors that barely changed are not in the file. Its length is therefore not the number of sectors in the release.

### Release reports

The release job leaves reports for every release at `{{reportsURI}}`, a host this sandbox cannot reach. Those a
review needs are mounted read-only, for this release and for the one it is compared against:

{{releaseReports}}

A report that is not available cannot be checked - say so in the report rather than working around it.

**Job log digests.** A release log runs to several GB, almost all of it one line per identifier. The digest
keeps what a reviewer needs: the lines per level and logger; a timeline quoting, with timestamps, every line of
the loggers that log little - the steps the job went through, their counts, every warning and error among them
- together with the rare messages of the loggers that log a lot; and for each of those a summary of its count,
its message patterns and its first and last lines. DEBUG lines are only counted. Credentials are redacted - never
try to recover one.

**ID reports.** Tab separated, no header, with the columns `ID`, `rank`, `status`, `name`, `authorship`:

- `created.tsv` - identifiers the release issued for the first time, with the name they now carry.
- `deleted.tsv` - identifiers of the release before it that it no longer uses, with the name they had there.
- `base-deleted.tsv` - extended releases only: identifiers the release before it had, but the base release it
  extends dropped already. An extended release keeps every identifier of its base release, so these are the base
  release's changes, not its own, and are left out of `deleted.tsv`.
- `resurrected.tsv` - identifiers of an older release, not used by the release before it, that are used again, with
  the name they now carry.
- `superseded.tsv` - deleted identifiers and the identifier that took over from them, with the columns `ID`,
  `newID`, `rank`, `status`, `name`, `authorship` of the name now carrying `newID`.

and `unstable.txt`, the names whose identifier changed: the name on a line of its own, followed by one line per
usage, `-` for an identifier that went away and `+` for the one that replaced it, e.g.

```
Abacetus biimpressus
 - Abacetus biimpressus Straneo, 1951 [SYNONYM SPECIES 2328:8HTM nidx=null parent=5DDVS]
 + Abacetus biimpressus Straneo, 1951 [SYNONYM SPECIES 9837:8KZPV nidx=null parent=8MDJK]
```

The ID reports can hold hundreds of thousands of lines. Count and filter them with `wc`, `cut`, `sort | uniq -c`
and `grep` instead of reading them whole.

### Endpoints you will need

| purpose | request |
|---|---|
| release metadata | `GET /dataset/{key}` |
| whole-release metrics (counts per rank, per status, issue counts) | `GET /dataset/{key}/import?limit=1` |
| the sources that went into a release | `GET /dataset/{key}/source` |
| metrics of one source inside a release | `GET /dataset/{key}/source/{sourceKey}/metrics` |
| name diff between two releases | `GET /dataset/{key}/diff/{key2}?minRank=order&authorship=false&synonyms=false` |
| duplicate count | `GET /dataset/{key}/duplicate/count?category=uninomial&minSize=2&mode=STRICT&rankDifferent=false&status=accepted&rank=family` |
| search usages, e.g. by issue | `GET /dataset/{key}/nameusage/search?issue=X&limit=0` |
| browse the classification | `GET /dataset/{key}/tree` and `GET /dataset/{key}/tree/{id}/children` |

For a release, `/dataset/{key}/import` resolves to the project import attempt the release was built from, so it
works on both members of the pair.

## The checklist

Work through all of it. If a step cannot be answered - an endpoint errors, a source has no metrics - say so
explicitly in the report rather than silently dropping it.

1. **Totals.** Compare the two releases' taxon, synonym, bare name, reference, vernacular name and distribution
   counts. Report absolute and relative change. A release that shrinks overall needs an explanation.
2. **Totals per rank.** Compare `taxaByRankCount` and `synonymsByRankCount`. Pay particular attention to the
   ranks above genus: the number of families, orders, classes and phyla should be close to stable between two
   consecutive releases, and a change of more than a few percent there is nearly always a bug, not new science.
3. **Issue counts.** Compare `issuesCount` from both imports. List every issue whose count grew, with the
   absolute and relative change, worst first. Call out issues that appear for the first time.
4. **Sectors.** Read `{{sectorMetricsPath}}`. Report every `ZERO` and every `REMOVED` row individually - these
   are content that used to be in COL and is not any more. Summarise `DECREASED` by source dataset. Treat large
   `INCREASED` values with the same suspicion as decreases: a sector that tripled usually means duplicated
   content, not a better source.
5. **Sources.** For the sources behind the flagged sectors, and for the ten largest sources overall, compare
   `/dataset/{key}/source/{sourceKey}/metrics` between the two releases. Distinguish a source that legitimately
   published a new version from a source that failed to import and silently contributed its previous content or
   nothing at all.
6. **Duplicates above family.** Query `/dataset/{key}/duplicate/count` for each rank from the top of the
   hierarchy down to and including `family`, for both releases, with `category=uninomial`, `minSize=2`,
   `mode=STRICT`, `rankDifferent=false`, `status=accepted`. Duplicated higher taxa are a classic symptom of a
   sector attaching in the wrong place. Report any rank whose count grew.
7. **Name diff at order level.** `GET /dataset/{{releaseKey}}/diff/{{previousReleaseKey}}?minRank=order&authorship=false&synonyms=false`
   gives the names added and removed above order rank. Lost orders and newly invented orders both deserve a
   line in the report. If the diff is too large to quote, quote the counts and a representative sample.
8. **Identifier stability.** Stable identifiers are a promise COL makes to everyone who links to it. From the ID
   reports, count created, deleted and resurrected identifiers and unstable names, for this release and the
   previous one, and compare them. List every deleted identifier of an accepted name at genus rank or above
   individually, summarise the other deletions by rank and status, and call out any count far above the
   previous release's.
9. **The release job.** Read both job log digests. Report every ERROR and every WARN pattern of this release
   with its count and how that compares with the previous release, and anything in the timeline that says a
   step was skipped, failed or processed nothing. Compare the durations of the main steps between the two
   releases and mention any that changed a lot.
10. **Unexpected drops and spikes.** Anything from the steps above that moves by more than ~10% without an
   obvious cause. Say what you think caused it and how confident you are.

## The report

Write a single, self-contained HTML file to `{{outputPath}}`.

- **No external scripts, stylesheets, fonts or images.** Inline CSS in a `<style>` block. No `<script>` that
  loads anything remote, and preferably no JavaScript at all. The file is served as a static document from the
  ChecklistBank download host.
- Readable on a phone as well as a laptop, and legible when printed.
- Every number you state must come from a request you actually made or from a mounted file. Do not estimate,
  do not extrapolate, and never fill a gap with a plausible-looking figure. If you did not measure it, say so.

Structure it in this order:

1. **Verdict** - one short paragraph, and one of **publish** / **publish with caveats** / **do not publish**,
   with the single most important reason. Put it first; it is what gets read.
2. **Red flags** - a table of the things that need a human decision, worst first. Each row: what, the numbers,
   what you think it means, and a link into ChecklistBank so the reviewer can look for themselves.
3. **Totals and ranks** - the headline comparison, as a table with absolute and relative change.
4. **Issues** - the issue counts that grew.
5. **Identifiers** - created, deleted, resurrected and unstable counts of both releases, and the deleted higher
   taxa.
6. **Release job** - the errors and warnings of the job, and anything its timeline shows went wrong.
7. **Per-source findings** - one subsection per source dataset that changed materially, with its sector(s),
   the numbers, and a link to the source in ChecklistBank.
8. **Everything checked and found fine** - a short list, so the reviewer can see what the review covered. A
   checklist item you could not complete belongs here too, marked as not checked and why.
9. **Method** - the release pair, the date, the endpoints and the mounted files you used.

Link back to the UI at `{{clbURI}}`, using these patterns:

- a dataset or release: `{{clbURI}}/dataset/{key}/about`
- its sources, and their metrics compared with the previous release:
  `{{clbURI}}/dataset/{key}/sourcemetrics?releaseKey={{previousReleaseKey}}`
- a sector: `{{clbURI}}/dataset/{{releaseKey}}/sector?sectorKey={sectorKey}`
- names carrying an issue: `{{clbURI}}/dataset/{key}/names?issue={ISSUE}`
- a single usage: `{{clbURI}}/dataset/{key}/taxon/{id}`
- duplicates: `{{clbURI}}/dataset/{key}/duplicates?category=uninomial&minSize=2&mode=STRICT&rankDifferent=false&status=accepted&rank={rank}`

and to the release reports of this release at `{{reportsURI}}/`, which is where the review itself is published.

Write the file, verify it exists and is valid HTML, and then stop. The file is the deliverable; nothing you say
in the conversation is kept.
