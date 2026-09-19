https://github.com/CatalogueOfLife/data/issues/1718

Genus homonyms are decided by lineage, not by the family rank alone.

Two usages sharing a canonical genus name but carrying different authorship are compared at the
**lowest rank present in both classifications, within a window from FAMILY up to ORDER**. Equal there
means the same genus; different there means real homonyms; sharing no rank in that window leaves the
decision to the taxonomic group (see below).

The real case is *Amanita*. COL's base genus comes from Species Fungorum, which supplies no authorship,
so the genus picks one up from whichever merge source runs first (`Dill. ex Boehm., 1760` in the
2026-09 XR). Flora e Funga do Brasil then merges `Amanita Pers.`, whose fungal branch carries **no
family rank at all** - its parent is the order Agaricales. The old `sameFamily` test demanded a FAMILY
on both sides and so returned false however well the rest of the lineage agreed, which produced the
duplicate genus reported in data#1718.

When the two share no rank in that window the taxonomic group gets the last word: disparate groups mean
different taxa whatever the ranks say, and only when the groups do not contradict each other either is
the placement genuinely undecidable.

The four genera here cover every branch:

| Genus | Target | Source | Lowest shared rank | Verdict |
|---|---|---|---|---|
| `Amanita` | `Dill. ex Boehm., 1760`, under Amanitaceae | `Pers.`, no family, under Agaricales | ORDER, `Agaricales` = `Agaricales` | **SAME** - reuse the target genus, merge the source species under it, leave its authorship alone |
| `Bus` | `Cameron, 1939`, under Staphylinidae | `Berthold, 1827`, under Tenebrionidae | FAMILY, `Staphylinidae` != `Tenebrionidae` | **CONFLICT** - real homonyms, create a second genus |
| `Dus` | `Mill.`, under Tenebrionidae (Animalia) | `Linn.`, straight under the phylum Tracheophyta (Plantae) | none in [FAMILY..ORDER], but the groups are **disparate** | **CONFLICT** - create a second genus |
| `Cus` | `Mill.`, under Tenebrionidae | `Linn.`, straight under the phylum Arthropoda | none in [FAMILY..ORDER], and the groups agree | **UNDECIDED** - skip the genus *and its whole subtree* |

`Cus secundus Linn.` is therefore absent from `expected.txtree`: the point of the undecided branch is
that a genus which cannot be placed with confidence contributes nothing rather than being duplicated.
It is logged as a warning and counted under `IgnoreReason.AMBIGUOUS_HOMONYM` in the sector import
metrics, so the loss is visible to curators.

`Dus` is the reason the undecided branch consults the taxonomic group at all: neither side offers a rank
between family and order, yet a vascular plant is plainly not a darkling beetle. `txtree/homonyms/`
covers the same branch with the real case, *Tubella Odin, 2008* sitting directly under the phylum
Calcitarcha next to a sponge and an orchid of the same name.

`Bus` is the guard in the other direction, mirroring `txtree/author-dupes/` (*Mycetochara* in
Tenebrionidae vs Staphylinidae): when both sides do carry a family, a family disagreement is decisive
and must still produce two genera. It also proves the check uses the lowest *shared* rank rather than
the lowest *agreeing* one - both `Bus` usages agree at ORDER (`Coleoptera`), and if that agreement were
allowed to stand in for the conflicting FAMILY the two beetles would wrongly collapse into one.
