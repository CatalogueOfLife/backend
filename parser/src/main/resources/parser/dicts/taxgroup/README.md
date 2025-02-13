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

For each of the groups we maintain a text file listing higher names down to families that unambiguously indicate such a group. 
For example 'Asteraceae' clearly point to [Angiosperms](https://github.com/CatalogueOfLife/backend/blob/master/parser/src/main/resources/parser/dicts/taxgroup/angiosperms.txt).


## Ambiguous names
The following taxon names are found as suprageneric names across different kingdoms and are therefore not indicative for any clear taxonomic group:

- Acanthocerataceae: Protists & Molluscs
- Bdelloidea: Rotifera & Arachnid -> Animals
- Cepheidae: Cnidaria & Arachnid -> Animals
- Chilodontidae: Gastropod & Chordate -> Animals
- Clionidae: Gastropod & Porifera -> Animals
- Heterocheilidae: Diptera & Nematodes -> Animals
- Heterogynidae: Lepidoptera & Hymenoptera -> Insects
- Hyperbionycidae: Protists & Arthropods
- Peranemataceae: Protists & Pteridophytes
- Personidae: Gastropod & Insetcs -> Animals
- Phyllophoridae: Echinodermata & Orthoptera -> Animals
- Sagittariidae: Protists & Chordate
- Tachinidae: Diptera & Coleoptera -> Insects
- Urostylidae: Protist & Hemiptera -> Eukaryote
- Cepolidae: Gastropod & Chordate -> Animals
- Leptosomatidae: Nematod & Bird -> Animals



## UNIX tools for managing
The ```clean.sh``` script goes through all dictionary files, sorts them and makes them unique.

In order to add new entries to a single dictionary and also remove potentially already existing entries from other files,
you can use the unix ```comm``` tool manually or run the ```merge.sh newnames chordates.txt``` script,
which requires new names to be added in a file called newnames and list the target dictionary as the 2nd argument.