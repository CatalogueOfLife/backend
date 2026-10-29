Pro parte synonyms get exploded into several usages/nodes that each have just one accepted taxon!
As the creation of multiple synonyms only happens during PgImport, we expect the pro parte ids as the phrase name here

The botanical autonym "Calendula incana Willd. subsp. incana" is expected in the tree as
"Calendula incana subsp. incana" without an author: an autonym has no author of its own (ICN Art. 26) and
the rust name parser keeps the species author (Willd.) apart as the specific authorship, which the Name
model does not keep - it is flagged as SUPERFLUOUS_AUTHORSHIP instead.

