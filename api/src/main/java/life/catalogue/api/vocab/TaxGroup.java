package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NomCode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Informal and common broad grouping of large taxonomic groups.
 * These groups often are paraphyletic, but are convenient for broad classifications
 * and match better the application of the nomenclatural codes, e.g. Algae, Fungi & Plants for the "botanical" code.
 */
public enum TaxGroup {
  Prokaryotes(NomCode.BACTERIAL),
    Bacteria(Prokaryotes),
    Archaea(Prokaryotes),

  Algae(NomCode.BOTANICAL), // sensu latu incl diatoms, but excluding prokaryotic cyanobacteria

  Plants(NomCode.BOTANICAL),
    Bryophytes(Plants), // sensu latu incl liverworts, hornworts and mosses
    Pteridophytes(Plants), // sensu latu with fern allies incl clubmosses, horsetails and whisk ferns
    Angiosperms(Plants),
    Gymnosperms(Plants),

  Fungi(NomCode.BOTANICAL),
    Ascomycetes(Fungi),
    Basidiomycetes(Fungi),
    Oomycetes(Fungi, NomCode.BOTANICAL), // traditionally follows fungal nomenclature therefore placed here. Phylogenetically related to Algae and protists
    OtherFungi(Fungi),

  Animals(NomCode.ZOOLOGICAL),
    Arthropods(Animals),
      Insects(Arthropods),
        Coleoptera(Insects),
        Diptera(Insects),
        Lepidoptera(Insects),
        Hymenoptera(Insects),
        Hemiptera(Insects),
        Orthoptera(Insects),
        Trichoptera(Insects),
        OtherInsects(Insects),
      Arachnids(Arthropods),//
      Crustacean(Arthropods),
      OtherArthropods(Arthropods),
    Molluscs(Animals),
    Chordates(Animals),
      Amphibians(Chordates),
      Birds(Chordates),
      Mammals(Chordates),
      Reptiles(Chordates),
      Fish(Chordates),
      OtherChordates(Chordates),
    OtherAnimals(Animals),

  Protists, // any eukaryote that is not animal, plant nor fungus

  Viruses(NomCode.VIRUS),

  Other;

  public static final Map<NomCode, Set<TaxGroup>> rootGroupsByCode;
  static {
    Map<NomCode, Set<TaxGroup>> map = new HashMap<>();
    for (NomCode code : NomCode.values()) {
      var groups = byCode(code);
      for (var g : groups) {
        if (g.parent == null) {
          map.computeIfAbsent(code, k -> new HashSet<>()).add(g);
        }
      }
    }
    rootGroupsByCode = Map.copyOf(map);
  }
  public static Set<TaxGroup> byCode(NomCode code) {
    return Arrays.stream(values())
          .filter(tg -> tg.getCode() == code)
          .collect(Collectors.toSet());
  }

  public static Set<TaxGroup> rootGroupsByCode(NomCode code) {
    return rootGroupsByCode(code);
  }

  private final TaxGroup parent;
  private final NomCode code;

  TaxGroup() {
    this.parent = null;
    this.code = null;
  }
  TaxGroup(NomCode code) {
    this.parent = null;
    this.code = code;
  }
  TaxGroup(TaxGroup parent) {
    this.parent = parent;
    this.code = parent.code;
  }
  TaxGroup(TaxGroup parent, NomCode code) {
    this.parent = parent;
    this.code = code;
  }

  public TaxGroup getParent() {
    return parent;
  }

  public NomCode getCode() {
    return code;
  }

  /**
   * @return true if the 2 groups are conflicting, i.e. are distinct sibling groups
   */
  public boolean isDisparateTo(TaxGroup other) {
    if (this == other || other == null) {
      return false;
    }
    var cl1 = classification();
    var cl2 = other.classification();
    if (cl1.containsAll(cl2) || cl2.containsAll(cl1)) {
      return false;
    }
    return true;
  }

  /**
   * True if other group is contained in the parent classification of the group or is the same group.
   */
  public boolean contains(TaxGroup other) {
    if (this == other) {
      return true;
    }
    var p = parent;
    while (p != null) {
      if (p == other) return true;
      p = p.parent;
    }
    return false;
  }

  public TaxGroup root() {
    var root = this;
    while (root.parent != null) {
      root = root.parent;
    }
    return root;
  }

  public Set<TaxGroup> classification() {
    Set<TaxGroup> cl = new HashSet<>();
    cl.add(this);
    var p = parent;
    while (p != null) {
      cl.add(p);
      p = p.parent;
    }
    return cl;
  }

  public int level() {
    int level = 1;
    var p = parent;
    while (p != null) {
      level++;
      p = p.parent;
    }
    return level;
  }
}
