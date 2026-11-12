# Registering datasets in GBIF

ChecklistBank can register an external dataset in the GBIF registry itself and keep the GBIF record up to date.
This gives checklist authors a route into GBIF when they have no IPT: they upload their data to ChecklistBank, or
let ChecklistBank import it from a URL, and register it from there. The design and its background are in
[2026-11-11-gbif-dataset-registration.md](2026-11-11-gbif-dataset-registration.md) and issue #1438.

## Who may register what

Two roles let a user act for a GBIF organisation. Both are granted by ChecklistBank admins and are independent of
each other:

| Role | User field | Grants |
|---|---|---|
| Organisation publisher | `orgPublisher` | Register datasets in GBIF under the organisation. It gives no access to any dataset. |
| Organisation editor | `orgEditor` | Read and edit all **external** datasets published by the organisation. Projects and releases are never included. |

Admins grant and revoke them with `POST` and `DELETE` on `/user/{key}/orgPublisher/{publisherKey}` and
`/user/{key}/orgEditor/{publisherKey}`:
- Granting checks that the organisation exists in GBIF, and stores it as a publisher so its title can be shown.
- Revoking changes nothing else, and no dataset is deregistered.

An admin grants the publisher role only once the organisation has agreed. That agreement is obtained outside
ChecklistBank.

To register a dataset, a user needs write access to it, normally because they created it, plus the organisation
publisher role for the chosen organisation. Admins may register any dataset under any organisation.

## What can be registered

The dataset must be:
- external, i.e. an upload or a URL import, and not a project, release or temporary dataset;
- public;
- licensed CC0, CC BY or CC BY-NC;
- not in GBIF already;
- imported successfully at least once, from a zip archive in a format GBIF knows: ColDP, DwC-A, ACEF or TextTree.

## The API

| Request | Who | Does |
|---|---|---|
| `POST /dataset/{key}/gbif` | editors of the dataset | Registers it. The body is `{"publisherKey": "<organisation>", "acceptTerms": true}`; `acceptTerms` confirms the [GBIF data publisher agreement](https://www.gbif.org/terms/data-publisher). |
| `GET /dataset/{key}/gbif` | editors of the dataset | The registration: the GBIF key, the organisation, who registered it and when, and any GBIF_REGISTRY command still waiting or failed. 404 if the dataset is not registered from ChecklistBank. |
| `PUT /dataset/{key}/gbif/publisher/{publisherKey}` | admins | Moves the dataset to another organisation. |
| `DELETE /dataset/{key}/gbif` | admins | Deregisters it, which deletes it in GBIF. |
| `POST /dataset/{key}/gbif/sync` | admins | Updates the GBIF record and asks GBIF to crawl again. |

Registering answers 503 unless `gbif.installationKey`, `gbif.username`/`gbif.password` and `apiURI` are all
configured.

## What GBIF gets

- **The dataset:** a GBIF checklist published by the chosen organisation and hosted by ChecklistBank's GBIF installation,
  `gbif.installationKey` (the "ChecklistBank" HTTP installation in production). It is created with the dataset's
  ChecklistBank DOI, so GBIF mints none.
- **A data endpoint:** `{apiURI}/dataset/{key}/archive.zip`. Without an attempt this serves the original archive of
  the last successful import, so a broken upload never reaches GBIF. Its type follows the format of that import:

  | Format of the import | GBIF endpoint type |
  |---|---|
  | ColDP | `COLDP` |
  | DwC-A | `DWC_ARCHIVE` |
  | ACEF | `ACEF` |
  | TextTree | `TEXT_TREE` |

  ChecklistBank generates no exports for GBIF.
- **An EML endpoint:** `{apiURI}/dataset/{key}.xml`, the metadata as ChecklistBank holds it, edits in ChecklistBank
  included.
- **The EML document, once:** on registration ChecklistBank also uploads the EML the endpoint serves
  (`POST /dataset/{gbifKey}/document`), so GBIF shows the metadata before its first crawl, whatever the format.
- **An identifier:** `CLB_DATASET_KEY`, with the ChecklistBank dataset key.

GBIF's crawler only crawls the endpoint it ranks highest, COLDP before EML before DWC_ARCHIVE, and it cannot crawl
TEXT_TREE or ACEF. The GBIF registry takes a dataset's metadata from the highest ranked document it holds, and EML ranks
above the `metadata.yaml` of a ColDP archive. So:
- For DwC-A, TextTree and ACEF datasets GBIF crawls the EML endpoint, which replaces the uploaded EML and passes on every
  later edit.
- For ColDP datasets GBIF crawls the ColDP archive and keeps showing the EML uploaded on registration. Later edits,
  in ChecklistBank or in the archive's `metadata.yaml`, do not reach GBIF. We asked GBIF on #1438 to read the EML
  endpoint for ColDP datasets too.

## Keeping GBIF up to date

Every change is passed to GBIF through the outbox, as a `GBIF_REGISTRY` command keyed by the dataset key. It is
written in the transaction of the change, and requests for the same dataset merge into one command. A run:
- updates the organisation and DOI of the GBIF record;
- makes its endpoints exactly the data and EML endpoints above;
- adds the identifier;
- asks GBIF to crawl.

What requests a command:
- **Registering the dataset:** a command straight away, which also uploads the EML document first.
- **Moving it to another organisation:** a command straight away.
- **A new import:** a command straight away, also when the dataset's metadata is locked.
- **A metadata edit:** a command after `gbif.registryUpdateDelay` seconds, 300 by default, so a burst of edits is
  passed on once.
- **Deleting the dataset or deregistering it:** a command that deletes the GBIF record. The registration keeps the GBIF
  key, which a dataset deletion removes from the dataset.

Commands wait while registering is not configured. A request GBIF refuses with a 4xx other than 429 fails the command
for good, for example bad credentials or a record deleted in GBIF. Fix the cause, then retry it under
`/admin/outbox`. Outages, 429 and 502-504 are retried.

While a dataset is registered it cannot be made private or given another licence than CC0, CC BY or CC BY-NC. Its
GBIF key and organisation are only changed through the endpoints above.

Creating the GBIF dataset is the one step that runs on the request rather than through the outbox. If recording the
registration fails afterwards, the GBIF dataset is deleted again. A timeout in the creation call itself, though, can
leave a GBIF dataset that ChecklistBank never learnt the key of. Each full GBIF registry sync logs every dataset
hosted by ChecklistBank's installation that no ChecklistBank dataset carries ("is hosted by ChecklistBank, but no
dataset carries its key"); delete those orphans in GBIF by hand.

## The GBIF registry sync

The GBIF registry sync never touches a GBIF dataset hosted by ChecklistBank's installation: it does not create, update,
write back to or delete one. It does not link one to a dataset by its `CLB_DATASET_KEY` either, as the COL record is
hosted there and names a release. This also protects the datasets registered by hand under the installation before.
The full sync's deletion of datasets that disappeared from GBIF skips registered datasets as well. A blocked dataset
in `gbif.blockedDatasets` is still deleted, and if it was registered from ChecklistBank that deletion reaches GBIF.

## Configuration

```yaml
apiURI: https://api.checklistbank.org
gbif:
  api: https://api.gbif.org/v1/
  username: ...                       # registry account with registry_admin rights
  password: ...
  installationKey: 3298f964-ee79-44d3-9ac3-4236aa62c5f9   # the ChecklistBank installation
  registryUpdateDelay: 300            # seconds
```

The outbox commands run on the app whose `Outbox` component is started.
