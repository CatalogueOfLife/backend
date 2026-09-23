# Merge publishedIn links into existing references

Date: 2026-09-23
Status: implemented on `feat/merge-pubin-links`, not yet deployed. No schema change.
Issue: https://github.com/CatalogueOfLife/backend/issues/1606

## Problem

A merge sector only gave an existing name its publishedIn reference when the name had none
(`TreeMergeHandler.updateName`, an empty `TODO: merge reference` otherwise). The BHL sector (dataset 310770, BHLnames:
automated links from COL names to their original description page, scored 4 or better) therefore lost its page link,
DOI and URL for almost every COL XR name: 267 of 292 sampled COL26.9 names already cite a reference, none has a page
link. Examples from the issue: Aaroniella madecassa (COL `Badonnel, A. (1967) Insectes Psocoptères. Faune de
Madagascar, 23, 1–235.` against BHL `Faune de Madagascar. (1967). Vol. 23`, both page 146) and Acacia citrinoviridis
(COL `Tindale, & Maslin. (1976).` against the BHL article in Nuytsia with a DOI).

Citations of the same work look very different across sources, so a link may only be added once the data asserts
both are the same. Adding a wrong link is worse than none.

## Evidence

300 random BHL names compared to the same COL26.9 name, 204 of which have a reference on both sides:

- BHL references come in two kinds: an *item*, a bound volume whose title is the journal or book and which is shared by
  every name in that volume, and a *part*, an article with its own title, container title and often a DOI.
- The name specific payload lives on the name: `publishedInPage` and `publishedInPageLink`.
- Page agreement - the name's page, the page after the volume in a citation (`10: 6 (1911)`), or a page within the
  article's range (`61(1):60-67`) - never pointed at a different work in the ~50 agreeing pairs checked by hand.
- A third of all pairs contradict: an off by one page, an index volume (Zoological Record, Just's Botanischer
  Jahresbericht), a different article with a different DOI, another volume. Those must merge nothing.
- A title only identifies an article. For an item the title is the journal name, which every citation of an article
  in it mentions as well.
- COL references often carry volume and issue in their page (`63(2), 207-213`) and BHL volumes often name a year range
  (`v. 9 (1907-16)`) or the first year of the whole serial as their year, with the volume's own year in its volume
  string (`(1857). 8 (1864)`).
- World Wide Wattle references are ~92% author & year stubs like `Benth. (1842).`, each shared by every name of that
  author and year.

Result on the sample: 65% of the pairs get page and page link, 1% also a DOI on the existing reference, 28% are
contradicted and 5% have no evidence either way.

## Decision

`PublishedInIdentity` compares both sides three valued like `NameIdentity`. A DOI, year (a year apart is tolerated,
but no evidence) or page difference contradicts and nothing is merged. Otherwise:

- **Same page or same work** (equal DOI, or article title and year agree): the name gets page and page link if it
  lacks them. Existing values are never overwritten.
- **Same work of an article** whose DOI the existing reference lacks: the DOI, and the URL if missing, go onto the
  existing reference. The reference is shared, so this needs work level evidence, and its citation is kept as it is -
  target references are looked up by exact citation, and re-rendering would break that.
- **Stub**: an existing reference holding nothing but the name's authors and year is treated like no reference, if
  the years agree. The name is pointed at the source reference instead, exactly what happens to a name without any
  reference. The stub itself stays untouched for all other names citing it.

Every change records the source as the `PUBLISHED_IN` secondary source of the name.

Merging a reference of one sector into names of another creates foreign pointers, which the name without a reference
already did before: re-syncing that sector in a project failed on `name.published_in_id` when `SectorSync.deleteOld`
deleted the sector's references. `NameMapper.removeForeignPublishedIn` now clears those pointers first; the sync sets
them again.

## Rejected

- Copying BHL item URLs or whole title DOIs (`10.5962/bhl.title.*`) onto article references: imprecise, and the
  reference is shared.
- Tolerating a page off by one: the link would point elsewhere than the page given.
- A volume veto: volume strings like `ser.3:v.13=no.73-78 (1864)` are too ambiguous to parse safely.
- Letting the page inside a botanical citation (`Novon 4(4): 350 (1994)`) justify a DOI on the reference. The citation
  denotes that page, so it would be sound, and is a possible follow-up.
- Making a copied reference belong to the sector of the name citing it, to avoid foreign pointers: the sector key of a
  record is its provenance.
