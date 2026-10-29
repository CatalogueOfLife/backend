A placeholder hanging off a real taxon is never merged, but its children attach to the taxon it is anchored on, as long
as that ranks above the placeholder:

- `Arthropoda incertae sedis` sits under Animalia in the source, its species go to Arthropoda
- `Insecta incertae sedis` is ranked a class like Insecta itself, so its species stay with the parent Animalia
- `Nemertea incertae sedis` is anchored on a taxon the project does not have, its species stay with Animalia
- `Mollusca incertae sedis` is a root of the source, its species go to Mollusca
