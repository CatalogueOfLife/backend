# ChecklistBank API guide

In addition to the code generated [Swagger API documentation](https://api.checklistbank.org) we collect here examples of how to use the ChecklistBank (CLB) API for common use cases.


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
