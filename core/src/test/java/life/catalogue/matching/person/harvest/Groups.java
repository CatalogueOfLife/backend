package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import static java.util.Map.entry;
import static life.catalogue.api.vocab.TaxGroup.*;

/**
 * Maps what an authority records a person worked on to taxonomic groups. Only authorities are asked: an author string
 * is not a person, and mining our own names would give an ambiguous string the groups of everyone who shares it.
 */
final class Groups {
  private static final Map<String, Set<TaxGroup>> IPNI = Map.of(
    "Spermatophytes", Set.of(Angiosperms, Gymnosperms),
    "Pteridophytes", Set.of(Pteridophytes),
    "Bryophytes", Set.of(Bryophytes),
    "Algae", Set.of(Algae),
    "Mycology", Set.of(Fungi)
  );
  /** IPNI values that are no group of organisms */
  private static final Set<String> IPNI_IGNORED = Set.of("Fossils", "Pre-Linnaean");
  /**
   * Wikidata fields of work (P101) by their English label. Botany keeps fungi: botanists described them for centuries.
   */
  private static final Map<String, Set<TaxGroup>> FIELDS = Map.ofEntries(
    entry("botany", Set.of(Plants, Fungi)),
    entry("phycology", Set.of(Algae)),
    entry("bryology", Set.of(Bryophytes)),
    entry("pteridology", Set.of(Pteridophytes)),
    entry("mycology", Set.of(Fungi)),
    entry("lichenology", Set.of(Fungi)),
    entry("zoology", Set.of(Animals)),
    entry("entomology", Set.of(Insects)),
    entry("coleopterology", Set.of(Coleoptera)),
    entry("lepidopterology", Set.of(Lepidoptera)),
    entry("dipterology", Set.of(Diptera)),
    entry("hymenopterology", Set.of(Hymenoptera)),
    entry("myrmecology", Set.of(Hymenoptera)),
    entry("arachnology", Set.of(Arachnids)),
    entry("acarology", Set.of(Arachnids)),
    entry("carcinology", Set.of(Crustacean)),
    entry("malacology", Set.of(Molluscs)),
    entry("conchology", Set.of(Molluscs)),
    entry("ichthyology", Set.of(Chordates)),
    entry("herpetology", Set.of(Chordates)),
    entry("ornithology", Set.of(Chordates)),
    entry("mammalogy", Set.of(Chordates)),
    entry("protistology", Set.of(Protists)),
    entry("protozoology", Set.of(Protists)),
    entry("bacteriology", Set.of(Bacteria)),
    entry("virology", Set.of(Viruses))
  );

  private Groups() {
  }

  /**
   * @param taxonGroups IPNI's "Mycology, Spermatophytes, Algae"
   * @param unmapped    receives every value that is neither mapped nor known to be no group
   */
  static Set<TaxGroup> ipni(@Nullable String taxonGroups, Consumer<String> unmapped) {
    Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    if (taxonGroups == null) return groups;
    for (String g : taxonGroups.split(",")) {
      String v = g.trim();
      if (v.isEmpty() || IPNI_IGNORED.contains(v)) continue;
      Set<TaxGroup> mapped = IPNI.get(v);
      if (mapped == null) {
        unmapped.accept(v);
      } else {
        groups.addAll(mapped);
      }
    }
    return groups;
  }

  /**
   * @return the groups of a Wikidata field of work, none for a field that is no group of organisms
   */
  static Set<TaxGroup> field(String englishLabel) {
    return FIELDS.getOrDefault(englishLabel.trim().toLowerCase(), Set.of());
  }
}
