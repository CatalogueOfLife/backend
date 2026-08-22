Pro parte synonyms get exploded into several usages/nodes that each have just one accepted taxon!
As the creation of multiple synonyms only happens during PgImport, we expect the pro parte ids as the phrase name here

The botanical autonym "Calendula incana Willd. subsp. incana" is expected in the tree as
"Calendula incana subsp. incana Willd." — name-parser v4 captures the species author (Willd.) on the
autonym, and the text tree renders authorship after the full canonical name (author-at-end), matching
the convention used by the other autonym fixtures. The botanically correct placement (author after the
species epithet) is not produced by the flat text-tree label.

