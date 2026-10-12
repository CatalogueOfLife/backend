https://github.com/CatalogueOfLife/backend/issues/1608

Species must not be merged into a homonym genus of another kingdom or order just because the genus names match.

The project holds genus homonyms in disparate groups: *Platygaster* in the wasps and, unauthored, in the gall midges,
*Paracoccus* as a scale insect and as a bacterium, *Oribates* as a mite and as a moth synonym of *Gyros*.

`article` is a Plazi style paper about a disease of a grass. It files the fungus *Curvularia* under Poaceae, the family
of its host. The import flagged that genus with a `PLACEMENT_CONFLICT` (the `ISSUE` info): the COL base release knows
*Curvularia* only as a fungus. The merge therefore matches the genus without its source classification, finds the single
project *Curvularia* and attaches the new species there, instead of creating a grass genus *Curvularia* that would
attract every later *Curvularia* species.

`species` gives species with nothing but their genus name, like ZooBank or the Koch datasets. Their implicit genus
cannot be told apart from homonyms of disparate groups, so

- *Platygaster mayi* and *Paracoccus rhizosphaerae* are skipped as `AMBIGUOUS_HOMONYM` - the merge used to pick a
  homonym at random, and the pick flipped between releases
- *Paracoccus aestuarii* carries the bacterial code, which settles it: it joins the bacterium
- *Oribates badius* joins the accepted mite genus, not the moth the synonym *Oribates* Edwards belongs to

*Paracoccus rhizosphaerae* deliberately has no year: an authorship like `Jung & Lee, 2021` reads as zoological, which
makes the name a eukaryote and rules out the bacterium - correctly, given such data.
