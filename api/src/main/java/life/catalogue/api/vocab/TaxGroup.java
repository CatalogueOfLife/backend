package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NomCode;

import java.util.HashSet;
import java.util.Set;

public enum TaxGroup {
  Prokaryotes(NomCode.BACTERIAL),
    Bacteria(Prokaryotes),
    Archaea(Prokaryotes),

  Plants(NomCode.BOTANICAL),
    Bryophytes(Plants),
    Lycophytes(Plants),
    Pteridophytes(Plants), // with horsetails
    Spermatophytes(Plants),
      Angiosperms(Spermatophytes),
      Gymnosperms(Spermatophytes),

  Fungi(NomCode.BOTANICAL),
    Ascomycetes(Fungi),
    Basidiomycetes(Fungi),

  Animals(NomCode.ZOOLOGICAL),
    Arthropods(Animals),
      Insects(Arthropods),
        Coleoptera(Insects),
        Diptera(Insects),
        Lepidoptera(Insects),
        Hymenoptera(Insects),
        Hemiptera(Insects),
        Orthoptera(Insects),
      Arachnids(Arthropods),
      Crustacean(Arthropods),
    Molluscs(Animals),
    Chordates(Animals),
      Birds(Chordates),
      Mammals(Chordates),
      Reptiles(Chordates),
      Fish(Chordates),

  Protists, // any eukaryote that is not animal, plant nor fungus

  Viruses(NomCode.VIRUS),

  Other;

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

  private Set<TaxGroup> classification() {
    Set<TaxGroup> cl = new HashSet<>();
    cl.add(this);
    var p = parent;
    while (p != null) {
      cl.add(p);
      p = p.parent;
    }
    return cl;
  }
}
