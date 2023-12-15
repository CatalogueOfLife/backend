# Taxonomic Group Parser Dictionaries
An important part of the name usage matching, apart from plain name matching, 
is to compare the classification of matched candidates to disambiguate homonyms. 
As classifications can be very different in some parts or exist only patchy 
the algorithm tries to match each higher taxon to a limited, hand selected 
set of [hierarchical taxonomic groups](https://github.com/CatalogueOfLife/backend/blob/master/api/src/main/java/life/catalogue/api/vocab/TaxGroup.java) 
which are selected to keep the major parts apart, e.g plants and animals. 
The number of groups are kept as low as possible and do not have to represent actual taxonomic groups or even be monophyletic.
Instead they are largely selected based on size of included species. 
In addition to named groups there are also "outgroups", siblings that pool all other names in smaller or debated groups. 
The groups are called OtherXyz. 

For each of the groups we maintain a text file listing higher names down to families that unambiguously indicate such a group. For example Asteraceae clearly point to Angiosperms.

ChecklistBank has a tool to analyse all higher names and report those that currently are not listed in any of the files.
Go through at least the names down to class, better order, and add them to the respective parser files.


## Ambiguous names
The following taxon names are found across different kingdoms and are therefore not indicative for any clear taxonomic group:

- Acanthocerataceae: Protists & Molluscs
- Hyperbionycidae: Protists & Arthropods
- Peranemataceae: Protists & Pteridophytes
- Sagittariidae: Protists & Birds