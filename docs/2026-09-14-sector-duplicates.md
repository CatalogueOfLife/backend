# Sector duplicates: allow, then report

Date: 2026-09-14
Status: implemented on `feat/sector-duplicates`, not yet deployed. Needs the `dbschema.md` migration of the same date.
Issue: https://github.com/CatalogueOfLife/backend/issues/1581

## Problem

A project could not have two sectors with the same subject: `sector` carried
`UNIQUE (dataset_key, subject_dataset_key, subject_id)`. In the RCE project a vernacular only MERGE sector on
Plantae was rejected with `Sector already exists`, because an ATTACH sector on Plantae already existed.
`SectorDao.create` additionally allowed only one subject less MERGE sector per source (#1560).

Since sectors gained entity, rank, name type and status filters, two sectors on one subject can be perfectly
intended. Blocking them is wrong, but large projects like COL still want to find real duplicates.

## Decision

Do not block, report instead.

- The unique constraint is gone. So is the subject less merge guard in `SectorDao.create`.
- The **one HIERARCHY sector per project** guard stays. It is not a duplicate check: a project's higher
  classification can only be delegated to a single sector.
- `GET /dataset/{key}/sector?duplicates=true` restricts the regular, flat sector search to sectors that share
  their subject with another sector.
- `GET /dataset/{key}/sector/duplicate` returns the same sectors as nested groups
  `{datasetKey, subjectDatasetKey, subjectId, sectors}`, paging over groups. It follows the name usage
  `/dataset/{key}/duplicate` resource.

Semantics shared by both:

- The duplicate key is `(dataset_key, subject_dataset_key, subject_id)`. A NULL subject id counts as equal, so
  subject less sectors from the same source form a group.
- All other search filters apply **before** duplicates are detected. With `mode=MERGE` you only get merge
  sectors sharing a subject with another merge sector. This keeps the flat filter and the groups identical.
  The flat search uses a window count and the groups a `GROUP BY`. Both treat NULLs as equal.

## Consequences in code that assumed one sector per subject

- `SectorMapper.getBySubject` became `listBySubject`, ordered by priority. `TreeDao` picks the first sector with
  the matching placeholder rank.
- The source tree (`TreeMapper`, `Type.SOURCE`) joins the most important non-merge, non-placeholder sector per
  node with a lateral subquery. Otherwise a node would be listed once per sector.
- `SectorRematcher` used to keep a sector broken rather than relink it to a subject that another sector already
  used. It now relinks.

## Not done

- No warning on create. The UI can check `/sector/duplicate` itself.
- Subjects that merely overlap, such as nested subjects or the same name under another id, are not reported.
