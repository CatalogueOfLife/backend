# Taxonomic Group Parser Dictionaries
An important part of the name usage matching, apart from plain name matching, 
is to compare the classification of matched candidates to disambiguate homonyms. 
As classifications can be very different in some parts or exist only patchy 
the algorithm tries to match each higher taxon to a limited, hand selected 
set of [hierarchical taxonomic groups](https://www.checklistbank.org/vocabulary/taxgrouptree) 
which are selected to keep the major parts apart, e.g plants and animals. 
The number of groups are kept as low as possible and do not have to represent actual taxonomic groups or even be monophyletic.
Instead they are largely selected based on size of included species. 
In addition to named groups there are also "outgroups", siblings that pool all other names in smaller or debated groups. 
The groups are called OtherXyz. 

For each of the groups we maintain a text file listing higher names down to families that unambiguously indicate such a group. 
For example `Asteraceae` clearly point to [Angiosperms](https://github.com/CatalogueOfLife/backend/blob/master/parser/src/main/resources/parser/dicts/taxgroup/angiosperms.txt).

## Sources for looking up names
Various taxonomic sources can be consulted for the classification of names
and to see if there are homonyms or other ambiguous names.

Use the following to query for "Acanthoecaceae":
 - https://www.catalogueoflife.org
 - https://www.checklistbank.org
 - https://www.marinespecies.org
 - https://www.algaebase.org
 - Wikipedias
   - https://en.wikipedia.org/
   - https://de.wikipedia.org/
   - https://fr.wikipedia.org/
 - https://www.wikidata.org
 - https://www.ncbi.nlm.nih.gov/taxonomy/?term=Acanthoecaceae
 - https://www.irmng.org
 - https://www.inaturalist.org/taxa
 - https://www.opentreeoflife.org
 - https://www.gbif.org

## Evidence comments
Every entry carries a comment after `#` with the source that places it in its group, as found by an exact name search
across all ChecklistBank datasets: the dataset alias followed by the higher classification it gives, or the accepted
name for a synonym, e.g. `Abacina # COL: synonym of Pterostichina (Coleoptera)`. COL is preferred, then ITIS, WoRMS,
IRMNG, GBIF and PBDB. An entry of an ambiguous name gives one source per group it is used in, and names no dataset knows,
mostly vernacular names and misspellings, say `not found in ChecklistBank`. Any older explanation stays in front.

## Ambiguous names
A dictionary entry must point to one group for certain. A name used for taxa in several disparate groups,
a homonym across kingdoms, phyla or orders above all, is therefore listed only in the dictionary of the lowest group
common to all its uses, e.g. `Tachinidae` (Diptera and Coleoptera) as insects, `Cepheidae` (Cnidaria and mites) as animals
and `Carinae` (beetles and sedges) as eukaryotes. A name without any common group is not listed at all.
Taxonomic status does not matter: a junior homonym or any other synonym is just as likely to turn up in a classification
as an accepted name.

All names known to be ambiguous are recorded in [`ambiguous.txt`](ambiguous.txt) with the groups they are used in,
e.g. `Tachinidae # Coleoptera, Diptera`. The parser does not read that file, but `TaxGroupParserTest` checks that each of
its names is listed in exactly its lowest common group. Before adding a name to a dictionary, check it is not
recorded there, and record newly found homonyms in it.

## UNIX tools for managing dictionaries
The ```clean.sh``` script goes through all dictionary files, sorts them and makes them unique ignoring potential comments.

The TaxGroupParserTest.java class contains a test suite that can be used to check if the dictionaries are consistent
and do not have overlapping entries.

In order to add new entries to a single dictionary and also remove potentially already existing entries from other files,
you can use the unix ```comm``` tool manually or run the ```merge.sh newnames chordates.txt``` script,
which requires new names to be added in a file called newnames and list the target dictionary as the 2nd argument.