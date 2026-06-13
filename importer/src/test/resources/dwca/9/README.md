Tests the relinking of chained synonyms that point to other synonyms.

The botanical autonym "Calendula incana Willd. subsp. incana" is expected in the tree as
"Calendula incana subsp. incana Willd." — name-parser v4 captures the species author (Willd.) on the
autonym, and the text tree renders authorship after the full canonical name (author-at-end), matching
the convention used by the other autonym fixtures. The botanically correct placement (author after the
species epithet) is not produced by the flat text-tree label.