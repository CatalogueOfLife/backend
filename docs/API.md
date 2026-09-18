# ChecklistBank API guide

In addition to the code generated [Swagger API documentation](https://api.checklistbank.org) we collect here examples of how to use the ChecklistBank (CLB) API for common use cases.
The ChecklistBank API is accessible at https://api.checklistbank.org, 
but we also provide a development installation for testing: http://api.dev.checklistbank.org

## Introduction
ChecklistBank (CLB) was designed to store many different datasets, often called checklists, that are dealing with scientific names at their core.
In order to retain the original record identifiers - which might also occurr in other datasets - 
identifiers are only unique within a single dataset and compound keys are used to address a record uniquely in CLB.

Most API resources are therefore scoped by a dataset key like this:

 - https://api.checklistbank.org/dataset/9910/name
 - https://api.checklistbank.org/dataset/9910/taxon/H6
 - https://api.checklistbank.org/dataset/9910/reference?q=Wetter


ChecklistBank differes between *names* and *name usages* which can be a *taxon* (=accepted name) or a *synonym*.
Names can exist on their own in a dataset, e.g. if they are taxonomically unplaced or part of a nomenclatural dataset.
We refer to these names as *bare names*.


## Datasets
Knowing the datasetKey of a dataset to work with is important to access it's data.
CLB user interface offers a dataset search with various filters which you can also access via the API:

https://api.checklistbank.org/dataset?q=hobern

This finds datasets with Donald Hobern as an author or contributor.
The ```q``` parameter is a weighted full text search, which hits the alias, title, description, but also creators, editors, publisher and other fields of the dataset metadata.
Data itself, e.g. scientific names, are not searched.

Popular dataset filters:

 - `origin=PROJECT`: find project datasets only
 - `type=LEGAL&type=PHYLOGENETIC`: find datasets of type LEGAL or PHYLOGENETIC. 
 - `releasedFrom=3`: list only releases of project 3, which is the Catalogue of Life (COL)
 - `contributesTo=3`: list only datasets that are sources of project 3, which is the Catalogue of Life
 - `gbifKey=d7dddbf4-2cf0-4f39-9b2a-bb099caae36c`: find dataset with the [GBIF key d7dddbf4-2cf0-4f39-9b2a-bb099caae36c](https://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c)
 - `gbifPublisherKey=7ce8aef0-9e92-11dc-8738-b8a03c50a862`: find [datasets](https://www.checklistbank.org/dataset?gbifPublisherKey=7ce8aef0-9e92-11dc-8738-b8a03c50a862&limit=50&offset=0&origin=external&origin=project) which have been published in GBIF by the given publisher, in this case [Plazi](https://www.gbif.org/publisher/7ce8aef0-9e92-11dc-8738-b8a03c50a862)

We synchronise ChecklistBank with [checklist datasets in GBIF](https://www.gbif.org/dataset/search?type=CHECKLIST), so you have access to all these GBIF datasets from CLB.

ChecklistBank distinguishes 3 kind of datasets indicated by their ```origin``` property:

 - ```external```: datasets which are maintained outside of ChecklistBank and are imported for read accecss only. This is the vast majority of all datasets
 - ```project```: datasets which are maintained inside ChecklistBank and which often include & sync data from other sources. The Catalogue of Life checklist is such a project with datasetKey=3
 - ```release```: immutable snapshots of a project with stable identifiers

The API also provides some simple magic dataset keys, that will allow you to access some datasets without knowing the latest key:

 - ```{KEY}LR```: a substitute for the latest, public release of a project, e.g the latest COL checklist: https://api.checklistbank.org/dataset/3LR 
 - ```COL{YEAR}```: a substitute for the annual release of the COL checklist in the given year with 4 digits: https://api.checklistbank.org/dataset/COL2023


## Vocabularies
There are many places in the API where a controlled vocabulary is used.
You can find an inventory of all vocabularies here: http://api.checklistbank.org/vocab
In order to see all supported values for a vocabulary append its name to the vocab resource, e.g. http://api.checklistbank.org/vocab/taxonomicstatus


## Authentication
The majority of the API is open and can be accessed anonymously.
Writing data often requires authentication and datasets can be `private`, i.e. are only visible to authorised users.

ChecklistBank shares user accounts with GBIF, so you need to have a [GBIF account](https://www.gbif.org/user/profile) to authenticate to the CLB API.

The **development environment is separate**: `api.dev.checklistbank.org` is linked to the GBIF development registry at [gbif-test.org](https://www.gbif-test.org), not to production GBIF. A production GBIF account will not work against dev — you need to register a separate account at [www.gbif-test.org/user/profile](https://www.gbif-test.org/user/profile) and then log in once at [dev.checklistbank.org](https://dev.checklistbank.org) with those credentials.

Authentication in the API uses simple [BasicAuth](https://en.wikipedia.org/wiki/Basic_access_authentication), but mostly for user interfaces we also provide [JWT](https://jwt.io/introduction).
Note that BasicAuth in itself is not very secure, so please use it always with the *https* protocol. 
CLB will actually decline the use of plain *http*.

A simple basic authentication using curl would look like this:

```bash
curl -s -v --user j.smith:passwd1234xyz "https:api.checklistbank.org/user/me"```
```

```bash
 > GET /user/me HTTP/2
 > Host: api.checklistbank.org
 > authorization: Basic ai5zbWl0aDpwYXNzd2QxMjM0eHl6
 > user-agent: curl/7.85.0
 > accept: */*
 > 
 < HTTP/2 401 
 < date: Fri, 30 Jun 2023 07:48:10 GMT
 < content-type: application/json
 - Authentication problem. Ignoring this.
 < www-authenticate: Basic realm="COL"
 < www-authenticate: Bearer token_type="JWT" realm="COL"
 < cache-control: must-revalidate,no-cache,no-store
 < content-length: 52
 < x-varnish: 296268277
 < age: 0
 < via: 1.1 varnish (Varnish/6.0)
 < x-cache: pass uncacheable
 < vary: Origin
```

Note that this user does not exist and a 401 is therefore returned.


## General API features
The API primarily provides RESTful JSON services documented with OpenAPI: http://api.checklistbank.org/openapi. 
When using it from the terminal [curl](https://curl.se/docs/manpage.html) and [jq](https://jqlang.github.io/jq/) are good friends, but there are also
other advanced clients like [Insomnia](https://insomnia.rest) or [RapidAPI](https://paw.cloud) that you can use.
These rich clients usually allow you to read in our [OpenAPI description](http://api.checklistbank.org/openapi) so they know already about all existing API methods!

In some areas other formats are also supported which can be requested by setting the appropriate http `Accept` header.
To simplify usage of the API when you do not have access to modifying request headers, the API also accepts various URL resource suffices which are converted into the matching Accept header:

 - ```.xml```: ```Accept: application/xml```
 - ```.json```: ```Accept: application/json```
 - ```.yaml```: ```Accept: text/yaml```
 - ```.txt```: ```Accept: text/plan```
 - ```.html```: ```Accept: text/html```
 - ```.tsv```: ```Accept: text/tsv```
 - ```.csv```: ```Accept: text/csv```
 - ```.zip```: ```Accept: application/zip```
 - ```.png```: ```Accept: image/png```
 - ```.bib```: ```Accept: application/x-bibtex```
 - ```.csljs```: ```Accept: application/vnd.citationstyles.csl+json```

For example you can retrieve dataset metadata in JSON, YAML or EML XML that way:

 - https://api.checklistbank.org/dataset/9910.json
 - https://api.checklistbank.org/dataset/9910.yaml
 - https://api.checklistbank.org/dataset/9910.xml

In most searches and list results the API offers a `limit` and `offset` parameter to support paging.
Not that paging is restricted to a maximum of 100.000 records.


## Searching for names in the Catalogue of Life checklist
The Catalogue of Life checkist is a project in ChecklistBank with the datasetKey=3.
Projects are living datasets that can change at any time, might temporarily have duplicate or bad data and therefore also do not use stable identifiers.
For regular use only immutable releases should be used, which are created on a monthly basis for COL.
Monthly releases will not change, but they might be deleted at some point after a minimum retention of one year.
Once a year the Catalogue of Life also releases an annual checklist with long term support, which will never be deleted. 

To search for a name usage in the annual release 2023 you can use the plain search:

 -  https://api.checklistbank.org/dataset/COL2022/nameusage/search?q=Abies

You can filter the search by various options:

 - rank: https://api.checklistbank.org/dataset/COL2022/nameusage/search?q=Abies&rank=species
 - content: what to search on, SCIENTIFIC_NAME, AUTHORSHIP or VERNACULAR_NAME. Can be multiple: https://api.checklistbank.org/dataset/COL2022/nameusage/search?q=Hobern&content=AUTHORSHIP
 - type: the kind of search to use. One of standard, exact or fuzzy: https://api.checklistbank.org/dataset/COL2022/nameusage/search?q=Hobern&content=AUTHORSHIP
   + *standard*: Combines whole-word matching with token-level prefix matching against any token in the scientific name. Also matches stems of common Latin/Greek compound prefixes (e.g. `mucid` finds `submucidus` and `pseudomucidus`). This is the default.
   + *exact*: Matches the entire search phrase to the entire scientific name.
   + *fuzzy*: A whole-words search against words of the scientific name, but accepts fuzzy matches with a small edit distance.
   + *Deprecated*: `whole_words` and `prefix` are still accepted as `type` values and are treated as `standard`. They will be removed in a future major release.


### Downloading search results
Searches are paged and, like any Elasticsearch query, cannot be paged arbitrarily deep.
To get *all* hits of a filtered search, request a download instead:

```
curl --user USERNAME:PASSWORD -X POST \
  "https://api.checklistbank.org/dataset/3/export/search?rank=species&status=accepted&issue=chained_synonym"
```

The endpoint accepts exactly the same request as the name usage search above, either as query
parameters or as a JSON body, so the query string of a ChecklistBank search page can be handed
over unchanged. Paging parameters are ignored - a download always covers the entire result set.
Authentication is required, but no special role.

It answers with the submitted job:

```json
{"key": "0f1a9c3e-...", "job": "SearchExport", "status": "waiting", "datasetKey": 3}
```

The job runs in the background and reads only from the search index, so even very large results
are fine. Track it and fetch the finished archive through the job API:

 - `GET https://api.checklistbank.org/job/{key}` - the current status
 - `GET https://api.checklistbank.org/job/{key}.zip` - the result, once the status is `finished`

You also get an email with the download link when it completes. The archive is a ColDP package
holding a `NameUsage.tsv` of all matched usages, the dataset `metadata.yaml`, and a `README.md`
describing the executed search together with the dataset citation and a link that repeats the
same search in ChecklistBank.


## Taxon info and vernacular names
You can get the vernacular names for a species, e.g. 4QHKG, through either the full taxon info:
http://api.checklistbank.org/dataset/COL2023/taxon/4QHKG/info

or individually through the vernacular name resource:
http://api.checklistbank.org/dataset/COL2023/taxon/4QHKG/vernacular

ChecklistBank also provides a basic vernacular search that finds vernacular names in an entire dataset:
http://api.checklistbank.org/dataset/COL2023/vernacular?q=Puma
http://api.checklistbank.org/dataset/COL2023/vernacular?q=Puma&language=spa


## Tree browsing
There is a specialised API to support navigating a taxonomic tree and to implement a tree browser.
This lists the root taxa of the tree - or better forrests:
https://api.checklistbank.org/dataset/COL2023/tree

For a specific taxon you can then list all of its direct children
https://api.checklistbank.org/dataset/COL2023/tree/N/children

Some taxa have mixed ranks as their direct children. 
For these the Tree API offers an option to create virtual placeholder nodes that group them under a single entry:
https://api.checklistbank.org/dataset/COL2023/tree/RT/children?insertPlaceholder=true

To load a tree from any given taxon, you can use the following resource that lists the parent classification:
https://api.checklistbank.org/dataset/COL2023/tree/33VS

Our reusable [React Tree component](https://github.com/CatalogueOfLife/portal-components/blob/master/README.md#colbrowsertree) makes use of this API.

## Name matching
The name matching API in ChecklistBank allows to match against any dataset in CLB which is identified by an integer dataset key. 
There are different resources for simple & batch matching.

The simple one by one matching resource takes various query parameters, the main one being a `q` or alternatively `scientificName` for the name:
https://api.checklistbank.org/dataset/3LR/match/nameusage?q=Abies

`3LR` stands for the *Latest Release* of dataset 3 which is the COL project.
So you will get the latest monthly release with that one without knowing it's actual integer key.
You can search for datasets to match against here: https://www.checklistbank.org/dataset

This gives you all releases of the COL checklist:
https://www.checklistbank.org/dataset?releasedFrom=3&sortBy=created

Annual releases of COL, which will be kept forever, can also be found by using COL + year as the dataset key, for example the annual release of 2022:
https://www.checklistbank.org/dataset/COL2022

Matching with an ambiguous "homonym" gives you no match, but shows the alternative options:
https://api.checklistbank.org/dataset/3LR/match/nameusage?q=Oenanthe

Adding an author or some classification helps to disambiguate in such a case:
https://api.checklistbank.org/dataset/3LR/match/nameusage?q=Oenanthe&kingdom=Plantae
https://api.checklistbank.org/dataset/3LR/match/nameusage?q=Oenanthe&authorship=Linneaus

Alternatively there is also a **bulk matching** method which creates an asynchroneous job similar to downloads.
You must have a user account to use it and will get an email notification once done. 
CLB user accounts are the same as GBIF accounts, therefore you need to register with GBIF and then log into ChecklistBank with the GBIF credentials once. 
The bulk matching is only available via the API at this stage, the UI will follow shortly.

Bulk matching accepts different inputs for names:

 1) upload a `CSV` or `TSV` file to supply names for matching or
 2) select a source dataset from ChecklistBank that you want to use to supply names for matching. This can then also be filtered by various parameters to just match a subtree, certain ranks, etc

 A bulk matching request could look like this:
 ```bash
   curl -s --user USERNAME:PASSWORD -H "Content-Type: text/tsv" --data-binary @match.tsv -X POST "https://api.checklistbank.org/dataset/COL2022/match/nameusage/job"
 ```
with a `match.tsv` input file such as this one:

 ```
 ID	rank	scientificName	authorship	kingdom
 tp	phylum	Tracheophyta		Plantae
 1	species	Abies alba	Mill.	Plantae
 2	species	Poa annua	L.	Plantae
 ```

 Query parameters for bulk matches from a source dataset:
   - `format`: CSV or TSV for the final result file
   - `sourceDatasetKey`: to request a dataset in CLB as the source of names for matching
   - `taxonID`: a taxon identifier from the source dataset to restrict names only from the subtree of that taxon, e.g. a selected family
   - `lowestRank`: the lowest rank to consider for source names. E.g. to match only species and ignore all infraspecific names
   - `synonyms`: if synonyms should be included, defaults to true
 
Query params for individual matches and column names in bulk input are called the same:
   - `id`
   - `scientificName` (or `q`)
   - `authorship`
   - `code`
   - `rank`
   - `superkingdom`
   - `kingdom`
   - `subkingdom`
   - `superphylum`
   - `phylum`
   - `subphylum`
   - `superclass`
   - `class`
   - `subclass`
   - `superorder`
   - `order`
   - `suborder`
   - `superfamily`
   - `family`
   - `subfamily`
   - `tribe`
   - `subtribe`
   - `genus`
   - `subgenus`
   - `section`
   - `species`

Authentification in the CLB API works either as plain `BasicAuth` for every request or you can request a `JWToken` which the UI for example does.
Basic API Docs https://api.checklistbank.org/#/default/match_1  

## Names Index

The _Names Index_ (nidx) is a technical component of ChecklistBank. It gives every distinct **canonical name** found in any dataset an integer id,
so that names can be linked across datasets. The name matching uses it as a first step to find candidate names quickly,
and then compares the authorship, rank and classification of those candidates itself.

The names index is not a nomenclator and far from a global list of names.
An entry is a normalized name string, not a name: it has no authorship, rank, nomenclatural code, status or publication.
Homonyms, names at different ranks and names that differ only in their gender ending or in frequent spelling variations all share one entry.
_Abies alba_ Mill. and _Abies alba_ (Aiton) Michx., two unrelated names, are the same entry.
The index also contains misspellings, informal names, identifiers and anything else that looks like a name in the source data, and nobody reviews its content.

Use nidx ids only to link names within ChecklistBank and don't store them for long.
Entries are never changed, but improvements to the name parser or the normalization require a rebuild of the index, which assigns new ids to all names.

### Single tier

Until August 2026 the index had two tiers: an entry for every distinct combination of name, rank and authorship,
each linked through a `canonicalId` to an unranked entry without authorship.
The specific tier has been removed. Every name now links directly to its canonical entry,
and homonyms are told apart by the name matching against a dataset, which compares the actual authorship and rank of the names.

For API users this means:

- `namesIndexId` on a name always points to a canonical entry. Where a `canonicalId` still appears next to it, e.g. in matching results, it has the same value.
- `/nidx/{id}` returns the canonical name, its normalized key and the full names matched to it, not a parsed name.
- `/nidx/{id}/group` and the ID mapping exports (`/nidx/export`) have been removed.
- `/nidx/match` is deprecated. Use the [name matching API](#name-matching) against a dataset instead.

### Building the canonical name

The canonical name is assembled from the parsed parts of a name:

- a uninomial stays as it is: _Abies_ Mill. → `Abies`
- a binomial or trinomial keeps the genus, the specific and the infraspecific epithet, plus a cultivar epithet if there is one:
  _Abies alba_ subsp. _apennina_ Brullo, Scelsi & Spamp. → `Abies alba apennina`, _Acer rubrum_ 'Armstrong' → `Acer rubrum Armstrong`
- an infrageneric name is reduced to its epithet: _Abies_ sect. _Grandis_ → `Grandis`.
  Sections of the same name in different genera therefore share one entry, see https://api.checklistbank.org/nidx/24727

Authorship, rank markers, hybrid signs, the _Candidatus_ prefix, a subgenus given in a binomial, qualifiers like _cf._ or _aff._
and any unparsed remainder of the name are dropped.
_Abies cf. alba_, _Abies aff. alba_ and _Abies alba_ Mill. all share one entry, and so do _Quercus_ × _rosacea_ and _Quercus rosacea_.

### Names that are not fully parsed

Names that cannot be broken down into a uninomial, binomial or infrageneric name use their full scientific name as the canonical name. This covers:

- indetermined and phrase names (name type `informal`), e.g. `Abies sp.` or `Pultenaea sp. 'Maryborough' (T.D.Stanley 87)`. `Abies sp.` is a separate entry from `Abies`.
- names the parser does not understand (name type `other`), e.g. virus names like `Tobacco mosaic virus`
- hybrid formulas (name type `formula`), e.g. `Salix alba × Salix fragilis`
- identifiers (name type `identifier`), e.g. BOLD BINs like `BOLD:AAA1200` or UNITE species hypotheses like `SH0864600.10FU`

The same normalization as for all other names applies (see below).
As lowercase words are stemmed too, the key for _Tobacco mosaic virus_ becomes `tobacco mosaic vir`.
If the authorship of an unparsable name is part of its name string, it also becomes part of the key,
so such names only link to names that are written the same way.

### Normalization

The canonical name is reduced to a normalized key, and all names with the same key share one entry:

1. Ligatures are split (`æ` → `ae`, `œ` → `oe`, `ß` → `ss`) and accents are removed (`é` → `e`, `ö` → `o`).
2. Hyphens, apostrophes, quotes, `?`, `!` and `_` are removed, `, . : ;` become spaces, and whitespace is collapsed.
3. The hybrid sign `×` is removed.
4. In names with several words, the first word (the genus or uninomial) is kept as it is.
   Every following word that starts with a lowercase letter is normalized further:
   - the Latin gender ending is removed: `alba`, `albus` and `album` become `alb`, `rubra` and `rubrum` become `ruber`
   - `j` and `y` become `i`, except at the start of a word
   - double letters become single ones: `apennina` → `apenina`
   - an `h` following a `g`, `r` or `t` is removed

   Words that start with an uppercase letter, e.g. cultivar epithets, are kept as they are.
5. The key is lowercased, and any character that is still not ASCII is replaced with `*`.

Some entries from the index with their keys:

| Canonical name | Normalized key |
|---|---|
| `Abies alba` | `abies alb` |
| `Abies alba apennina` | `abies alb apenin` |
| `Acer rubrum Albo-Variegatum` | `acer ruber albovariegatum` |
| `Pultenaea sp. 'maryborough' (t.d.stanley 87)` | `pultenaea sp mariboroug (t d stanlei 87)` |
| `Tobacco mosaic virus` | `tobacco mosaic vir` |
| `BOLD:AAA1200` | `bold aaa1200` |

The `scientificName` of an entry is the canonical name of whichever name created the entry first.
It is a label, not a corrected name: the entry for _Acer rubrum_ is currently labelled `Acer rubra`, see https://api.checklistbank.org/nidx/96130

### Names that are not indexed

Almost every name gets an entry, including misspelled, informal and unparsable names. The exceptions are:

- placeholder names (name type `placeholder`), e.g. _Asteraceae incertae sedis_ or _unknown genus_
- names without any Latin letter or digit, e.g. names written only in Chinese or Cyrillic script

These names have no `namesIndexId`.

### API resources

The metadata of the index with its id, creation date and size:
https://api.checklistbank.org/nidx/metadata

A names index entry with the full names matched to it and how often each of them occurs:
https://api.checklistbank.org/nidx/24074

```json
{
  "nidx": 24074,
  "normalizedName": "abies alb",
  "scientificName": "Abies alba",
  "labels": [
    { "label": "Abies alba Mill.", "count": 116 },
    { "label": "Abies alba (Aiton) Michx.", "count": 34 },
    { "label": "Abies alba", "count": 21 },
    { "label": "Abies alba (Aiton) Jess.", "count": 16 }
  ]
}
```

All usages of an entry across ChecklistBank with their classification. Deleted and temporary datasets and projects are left out:
https://api.checklistbank.org/nidx/24074/usages

The same usages through the name usage search:
https://api.checklistbank.org/nameusage?nidx=24074

Entries whose `scientificName` matches a regular expression anchored at the start of the name.
This searches the labels, not the normalized keys, so `Acer rubrum` does not find the entry labelled `Acer rubra`:
https://api.checklistbank.org/nidx/pattern?q=Abies%20alba

To find the entry for a given name, match the name against a dataset that contains it and read the `namesIndexId` of the result:
https://api.checklistbank.org/dataset/3LR/match/nameusage?q=Acer%20rubrum

## Github import hooks
Github repositories are very well suited to to host source data in ColDP or DwC archives if individual files do not exceed the 100MB limit.
Any changes to the data will be versioned and github automatically provides the archive as a zip package for all files.

For datasets managed in github ChecklistBank also offers a webhook that these github repositories can be configured for.
If setup correctly and change commit will trigger an import into ChecklistBank so that the data is kept up to date near realtime!

A new dataset can be configure with these steps:

 - in ChecklistBank: 
   - register the dataset in ChecklistBank and remember its DATASET_KEY for later
   - configure it's access URL to point to the github repo zip archive, e.g. https://github.com/CatalogueOfLife/data-vespoidea/archive/refs/heads/master.zip
 - in Github:
   - go to the repository settings and add a new webhook that points the payload URL to http://api.checklistbank.org/importer/{DATASET_KEY}/github and uses the `application/json` Content-Type.
   - configure github to use a secret that the CLB admin hands over to you confidently. Please request your secret with mdoering {at} gbif.org

