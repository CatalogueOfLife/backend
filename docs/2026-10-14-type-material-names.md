# Type material typifying several names

Date: 2026-10-14
Status: implemented on branch `feat/type-material-names`, not merged or deployed.
Issue: https://github.com/CatalogueOfLife/backend/issues/1645

## Problem
ColDP 1.2 allows a type specimen to typify several names: `TypeMaterial.nameID` is a comma concatenated list of name
ids, and `status` is either a single value applying to all of them or a comma concatenated list in the same order.
WFO (dataset 2004) uses this. CLB stored exactly one `name_id` and one `status` per `type_material` row, so the
importer looked up `"n1,n2"` as a single name id, flagged `NAME_ID_INVALID` and dropped the row.

## Decision
A normalised schema: one specimen row linked to any number of names, with the type status on the link, because the
spec gives a status per name.

- `type_material` holds the specimen and loses `name_id` and `status`.
- `type_material_name (dataset_key, type_material_id, name_id, status)` holds the links, hash partitioned like every
  other dataset table. Deleting a specimen cascades to its links. Deleting a name never cascades: links of a name are
  removed explicitly, as before, together with the specimens that typify no other name. Links carry no sector key,
  because a link is always created together with its specimen by the same import or sync.

The model has three classes:

| class | what it is | persisted as |
|---|---|---|
| `TypeMaterial` | the specimen with `List<TypifiedName> names` | `type_material` |
| `TypifiedName {nameId, status}` | one link | `type_material_name` |
| `TypeDesignation extends TypifiedName {typeMaterial}` | the read only, name scoped view | not persisted |

`TypeMaterialMapper` stays the one mapper for both tables. Its `create`, `update`, `deleteByName`, `deleteByTemp`,
`copyDataset` and `deleteOrphans` are default methods running one statement per table, and every read aggregates the
links into a json array, so cursors keep streaming without a sort or a join fanning out.

### API
Name scoped responses are designations: `GET /dataset/{key}/name/{id}/types` and the `typeMaterial` map of
`/taxon/{id}/info` (still keyed by name id) return `{nameId, status, typeMaterial}`, with the specimen embedded and
repeated when it typifies several names of the response. `TypeMaterial` itself has no `nameId` or `status`
anymore but a `names` list. This breaks the JSON of the checklistbank UI and the portal components, which change in
lockstep (`TypeMaterial` and `TypeMaterialPopover` in both).

The import metrics keep their names: `typeMaterialCount` counts specimens and `typeMaterialByStatusCount` counts
links, so a specimen counts once per name it typifies.

### Import
`ColdpInterpreter` splits `nameID` (trimmed, deduplicated, in order) unless the whole value is an existing name id,
which protects ids containing a comma. `status` is split only for several names: one value applies to all,
otherwise by position, and a count mismatch flags `TYPE_STATUS_INVALID` and gives names without a status `OTHER`.
`DataCsvInserter` drops links to unknown names with `NAME_ID_INVALID` and keeps the specimen as long as one name
is left, so a partially valid row is not `NOT_INTERPRETED`. DwC-A and TextTree produce single links.

### Sector sync, merge and releases
A source specimen is copied once per sync: `TypeMaterialIds`, owned by `TreeBaseHandler`, maps source to copy ids,
and the second copied name of the specimen only gets a link. Links to names the sync did not copy are not created.
The merge handler does the same per designation, compares designations rather than specimens for its holotype /
lectotype dedupe, and now always issues new ids instead of keeping the source id. Release copies copy the specimens
as they are and map the name ids of the links through `idmap_name_<key>`.

### Exports
The extended ColDP export writes one row per specimen with the comma concatenated usage ids of its exported names and
their status in the same order, collapsed to a single value when all agree. A subtree export, which fetches type
material in batches of names, writes a specimen only once.

## Migration
Two steps, as the old and the new app run side by side during a deploy, see `dbschema.md`: create and fill the link
table before the deploy, then catch up and drop the two columns once the old app is gone. taxon-ws reads only the
`typeMaterialCount` metric and is unaffected.

## Non-goals
- Existing copies of one specimen made per name are not merged. A re-import of a source that concatenates its name
  ids yields one specimen.
- TextTree imports still drop type material (`NameUsageData` does not copy `TxtUsage.typeMaterial`).
- The machine readable ColDP schemas in the coldp repo (`docs/datapackage.json` with its `nameID` foreign key and
  `status` enum, `templates/schema-pg*.sql`) still describe a single name and are not changed here.
