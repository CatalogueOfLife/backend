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

 - **EXTERNAL**: datasets which are maintained outside of ChecklistBank and are imported for read accecss only. This is the vast majority of all datasets
 - **PROJECT**: datasets which are maintained inside ChecklistBank and which often include & sync data from other sources. The Catalogue of Life checklist is such a project with datasetKey=3
 - **RELEASE**: immutable snapshots of a project with stable identifiers

The API also provides some simple magic dataset keys, that will allow you to access some datasets without knowing the latest key:

 - **{KEY}LR**: a substitute for the latest, public release of a project, e.g the latest COL checklist: https://api.checklistbank.org/dataset/3LR 
 - **COL{YEAR}**: a substitute for the annual release of the COL checklist in the given year with 4 digits: https://api.checklistbank.org/dataset/COL2023


## Vocabularies
There are many places in the API where a controlled vocabulary is used.
You can find an inventory of all vocabularies here: http://api.checklistbank.org/vocab
In order to see all supported values for a vocabulary append its name to the vocab resource, e.g. http://api.checklistbank.org/vocab/taxonomicstatus


## Authentication
The majority of the API is open and can be accessed anonymously.
Writing data often requires authentication and datasets can be `private`, i.e. are only visible to authorised users.

ChecklistBank shares user accounts with GBIF, so you need to have a [GBIF account](https://www.gbif.org/user/profile) to authenticate to the CLB API.
Authentication in the API uses simple [BasicAuth](https://en.wikipedia.org/wiki/Basic_access_authentication), but mostly for user interfaces we also provide [JWT](https://jwt.io/introduction).
Note that BasicAuth in itself is not very secure, so please use it always with the *https* protocol. 
CLB will actually decline the use of plain *http*.

A simple basic authentication using curl would look like this:

```curl -s -v --user j.smith:passwd1234xyz "https:api.checklistbank.org/user/me"```

>   > GET /user/me HTTP/2
>   > Host: api.checklistbank.org
>   > authorization: Basic ai5zbWl0aDpwYXNzd2QxMjM0eHl6
>   > user-agent: curl/7.85.0
>   > accept: */*
>   > 
>   < HTTP/2 401 
>   < date: Fri, 30 Jun 2023 07:48:10 GMT
>   < content-type: application/json
>   - Authentication problem. Ignoring this.
>   < www-authenticate: Basic realm="COL"
>   < www-authenticate: Bearer token_type="JWT" realm="COL"
>   < cache-control: must-revalidate,no-cache,no-store
>   < content-length: 52
>   < x-varnish: 296268277
>   < age: 0
>   < via: 1.1 varnish (Varnish/6.0)
>   < x-cache: pass uncacheable
>   < vary: Origin

Not that this user does not exist and a 401 is therefore returned.

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
 - content: what to search on, SCIENTIFIC_NAME, AUTHORSHIP or both: https://api.checklistbank.org/dataset/COL2022/nameusage/search?q=Hobern&content=AUTHORSHIP
 - type: the kind of search to use. One of prefix, whole_words or exact: https://api.checklistbank.org/dataset/COL2022/nameusage/search?q=Hobern&content=AUTHORSHIP
   + *prefix*: Matches a search term to the beginning of the epithets within a scientific name. This is the only available search type for the suggest service. Whole-word and exact matching defies the purpose of auto-completion. However, you still have the option of fuzzy/non-fuzzy matching.
   + *whole_words*: Matches a search term to entire epithets within a scientific name.
   + *exact*: Matches the entire search phrase to the entire scientific name. When choosing this type you cannot also opt for fuzzy matching. The "fuzzy" parameter is silently ignored.
 - fuzzy: allow for slightly fuzzy matching 


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
 ```
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
