# Registering ChecklistBank datasets in GBIF

Date: 2026-11-11
Status: implemented on branch `feat/gbif-registration`, not merged or deployed. The checklistbank UI and the deploy
configs follow separately.
Issue: [#1438](https://github.com/CatalogueOfLife/backend/issues/1438). Current behaviour: [GBIF-REGISTRATION.md](GBIF-REGISTRATION.md).

## Why

Taxonomists and small projects who keep a checklist in ChecklistBank, by uploading files or by an import from a URL,
have no IPT and no endorsed host to publish it in GBIF. GBIF's checklist processing runs on ChecklistBank now, so the
data already sits where GBIF reads it. Only the GBIF registration was missing.

Until now this was done by hand. Six datasets sit under GBIF's "ChecklistBank" HTTP installation:
- COL itself, whose endpoint is the export of a release;
- Systema Dipterorum and Species Fungorum Plus, with DwC-A export endpoints on ChecklistBank;
- Wikidata, Leipzig and a Sawflies dataset.

The ChecklistBank copies of the two DwC-A datasets only worked because someone set `gbifKey` by hand and switched on
`gbif sync lock`. GBIF's weekly crawl of Systema Dipterorum aborts, because its export URL 404s when no export exists
for the current import attempt.

## Decisions

Taken with Markus between 2026-11-04 and 2026-11-10, and discussed with GBIF on #1438.

- **Scope:** only external datasets. Projects and releases are out of scope; COL stays a hand registration.
- **Consent:**
  - Admins obtain an organisation's consent outside ChecklistBank and only record the grant.
  - No consent emails, no request or approval workflow, and no organisation tokens: ChecklistBank writes with its own
    registry account, as it already did for identifiers. Anne Hahn (GBIF) agreed the token is not needed.
  - COL is one organisation like any other.
- **Separate rights:** publishing and editorial rights are separate for every organisation.
  - The former `user.publisher` (edit all datasets of an organisation) became `orgEditor` and was narrowed to
    external datasets: the COL project carries the COL organisation key.
  - The new `orgPublisher` only allows registering.
  - Markus' reason: a COL publishing grant to many unrelated users must not make them editors of everything COL
    publishes. Curators of one organisation should not interfere with each other unless an admin says so.
- **Revoking a right** changes nothing automatically, follow-ups are manual.
- **Data endpoint:** always the latest original file ChecklistBank holds, the archive of the last successful import.
  No exports.
- **Metadata:** through an EML endpoint serving ChecklistBank's EML. Markus preferred that to uploading registry
  documents for simplicity on the ChecklistBank side. Changed on 2026-11-12, in two steps:
  - The EML document is uploaded on registration, so GBIF never shows a dataset without metadata, whatever it crawls.
  - It is uploaded after every successful import too, for every public dataset with a GBIF key, not only those
    ChecklistBank registered: datasets synced from GBIF and the hand registrations as well. The metadata in both
    systems should not drift apart for days or a week. For datasets ChecklistBank did not register this needs
    `gbif.bidirectional`, the existing switch for writing back to their GBIF records.
  - The uploads replace the GBIF DOI of datasets synced from GBIF with the ChecklistBank DOI, so both systems share
    one DOI.
- **DOI:** the GBIF dataset carries the ChecklistBank DOI, so GBIF mints none.

## GBIF facts the design rests on

Read from the source of `gbif/registry` and `gbif/crawler` on 2026-11-04 and 2026-11-10.
- `DatasetResource.create` mints a DOI only when none is given. Metadata updates keep the DOI unless the metadata
  yields one.
- The crawler crawls a single endpoint, the first by `EndpointPriorityComparator`: COLDP, DWC_DP, EML, DIGIR...,
  DWC_ARCHIVE, CAMTRAP_DP. TEXT_TREE and ACEF are not in the list and are never crawled.
- **Consequence:**
  - For DwC-A, TextTree and ACEF datasets GBIF crawls the EML endpoint and gets ChecklistBank's metadata.
  - For ColDP datasets it crawls the ColDP and reads its `metadata.yaml`. ColDP files without one, which includes all
    spreadsheet uploads, show no metadata in GBIF, and edits made in ChecklistBank do not reach GBIF for ColDP
    datasets.
  - We asked GBIF to read the EML endpoint for ColDP datasets too.
- `POST /dataset/{key}/document` stores one metadata document per type and updates the dataset from the preferred one,
  ranked EML, DC, COL_DP, DWC_DP. A document equal to the stored one changes nothing. The update replaces all contacts
  of the record, and the dataset's DOI with the first DOI of the document: its `packageId`, else the first DOI among the
  `alternateIdentifier`s (`DatasetWrapper.updatePrimaryDOI` in gbif-metadata-profile). Our EML lists the dataset's DOI
  first, and every ChecklistBank dataset has a ChecklistBank DOI, a GBIF DOI it came with being moved to its
  identifiers. Our uploads therefore replace the GBIF DOI of every dataset synced from GBIF with the ChecklistBank
  DOI. Markus wants exactly that, so both systems share one DOI. GBIF keeps a replaced GBIF DOI registered at DataCite as
  `IsPreviousVersionOf` the new one (`DatasetDoiDataCiteHandlingServiceImpl.datasetChanged`) and lists it as an
  alternate identifier.
- **Consequence of the uploads:**
  - For registered DwC-A, TextTree and ACEF datasets GBIF also crawls the EML endpoint, so edits between imports
    reach GBIF too.
  - For registered ColDP datasets the uploaded EML outranks the `metadata.yaml` GBIF reads from the archive. GBIF shows
    ChecklistBank's metadata as of the last import until it reads the EML endpoint for ColDP datasets as well.
  - For datasets published by others the upload replaces the EML GBIF's crawler took from the publisher's archive,
    until the next crawl replaces it in turn. Our EML is poorer than an IPT's: free text coverages, no temporal
    coverage, methods, project or maintenance.

## Design

- **Bookkeeping:** `gbif_registration` marks the datasets ChecklistBank registered and keeps who did it, which
  doubles as the acceptance of the publisher agreement. It also keeps the GBIF key, which a soft deletion nulls on the
  dataset.
- **Pushing changes:** every change goes through the outbox command `GBIF_REGISTRY`, keyed by the dataset key. It is
  written in the transaction of the change: registration, change of organisation, metadata update with a delay, new
  import, deletion and deregistration.
- **The handler is idempotent:** it makes the GBIF record match the dataset (organisation, DOI, exactly the two
  endpoints, the identifier) and requests a crawl, or deletes the record. The command of a registration or an import
  carries a `pushMetadata` flag, which survives merging, and uploads the EML before the rest. For a dataset
  ChecklistBank did not register it only uploads the EML.
- **Registering:** the GBIF dataset is created synchronously. If recording the registration fails afterwards, the GBIF
  dataset is deleted again.
- **Archive endpoint:** `/dataset/{key}/archive.zip` without an attempt now serves the archive of `dataset.attempt`.
  The `latest` symlink moves before an import runs, so it could point at a broken upload.
- **The GBIF→ChecklistBank sync:** it keeps hands off every dataset hosted by ChecklistBank's installation and never
  links one automatically. The full sync's delete pass skips registered datasets. Before, a registered dataset would
  have come back as a duplicate, been reimported from its own archive, or been deleted.
- **EML fixes:** the EML writes `shortName` before `title` and ISO country codes. A test reads it with GBIF's own EML
  parser.

## Rejected alternatives

- **Organisation tokens, as an IPT uses them.** ChecklistBank's registry account can write for any organisation, so
  the safeguard belongs in ChecklistBank's rights.
- **Consent emails sent by ChecklistBank, or approve links.** Admins handle consent themselves, which keeps
  ChecklistBank from mailing arbitrary addresses.
- **One right covering both publishing and editing, or a COL-only exception.** The danger is not specific to COL.
- **Exports as the endpoint, the way the hand registrations use them.** They 404 until an export exists for the
  current attempt, and GBIF should get what the author submitted.
- **Uploading the EML document to the registry on every metadata change (`POST /dataset/{key}/document`).** Uploads
  happen on registration and imports only. For datasets synced from GBIF, uploading on their metadata changes would
  feed the GBIF registry sync's own updates back to GBIF.
- **Pushing GBIF metadata fields with a PUT, locked for auto update.** That would mean mapping all metadata ourselves.
- **A cron job or a separate component with an event listener.** The outbox already gives transactional, durable,
  deduplicated commands with retries.
- **Linking hosted GBIF datasets to ChecklistBank datasets by their CLB_DATASET_KEY in the sync.** It would have
  attached COL's GBIF record to a release.

## Outcome

- **Left out:**
  - The checklistbank UI: a GBIF panel on the dataset options, and both organisation roles on the admin user page.
  - The deploy configs: the installation key for prod, and a GBIF UAT installation and account for dev and test.
  - An admin action adopting the hand registrations, which would also fix the Systema Dipterorum crawls.
- **Deploy:** the DDL in `dbschema.md` must run before the deploy. The old `user.publisher` column is dropped once the
  old apps are gone.
- **Open with GBIF:** the EML endpoint for ColDP datasets, and confirmation that GBIF crawling only the EML endpoint of
  DwC-A, TextTree and ACEF datasets is fine. Until the first is done, the metadata of a registered ColDP dataset in
  GBIF stays as it was on its last import.
