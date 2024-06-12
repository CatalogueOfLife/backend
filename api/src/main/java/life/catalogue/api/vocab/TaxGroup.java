package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NomCode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Informal and common broad grouping of large taxonomic groups.
 * These groups often are paraphyletic, but are convenient for broad classifications
 * and match better the application of the nomenclatural codes, e.g. Algae, Fungi & Plants for the "botanical" code.
 */
public enum TaxGroup {

  Viruses(NomCode.VIRUS),
  Prokaryotes(NomCode.BACTERIAL),
    Bacteria(Prokaryotes),
    Archaea(Prokaryotes),

  Eukaryotes(NomCode.ZOOLOGICAL, NomCode.BOTANICAL),

    /**
     * Any eukaryote that is not animal, plant (incl algae s.l.) nor fungus
     * Includes all Protozoa (ciliates, flagellates, and amoebas)
     */
    Protists(Eukaryotes),

    Plants(NomCode.BOTANICAL, Eukaryotes), // sensu lato incl red algae. More like Archaeplastida

      /**
       * Sensu lato including names traditionally treated as eukaryotic algae.
       * Includes diatoms, red algae and any protist algae, but excluding prokaryotic cyanobacteria
       */
      Algae(Plants, Protists),
      Bryophytes(Plants), // sensu lato incl liverworts, hornworts and mosses
      Pteridophytes(Plants), // sensu lato with fern allies incl clubmosses, horsetails and whisk ferns
      Angiosperms(Plants),
      Gymnosperms(Plants),

    Fungi(NomCode.BOTANICAL, Eukaryotes), // incl lichen
      Ascomycetes(Fungi),
      Basidiomycetes(Fungi),
      Oomycetes(Fungi), // traditionally follows fungal nomenclature therefore placed here. Phylogenetically related to Algae and protists
      OtherFungi(Fungi),

    Animals(NomCode.ZOOLOGICAL, Eukaryotes),
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
        Gastropods(Molluscs),//
        Bivalves(Molluscs),
        OtherMolluscs(Molluscs),
      Chordates(Animals),
        Amphibians(Chordates),
        Birds(Chordates),
        Mammals(Chordates),
        Reptiles(Chordates),
        Fish(Chordates),
        OtherChordates(Chordates),
      OtherAnimals(Animals),

    OtherEukaryotes(Eukaryotes);


  final Set<TaxGroup> parents = new HashSet<>();
  final Set<NomCode> codes = new HashSet<>();

  TaxGroup() {
  }

  TaxGroup(TaxGroup... parents) {
    if (parents != null) {
      for (var p : parents) {
        this.parents.add(p);
        this.codes.addAll(p.codes);
      }
    }
  }

  TaxGroup(NomCode code1, NomCode code2) {
    this.codes.add(code1);
    this.codes.add(code2);
  }
  TaxGroup(NomCode code, TaxGroup... parents) {
    this.codes.add(code);
    if (parents != null) {
      // dont inherit parent codes - we got an explicit one!
      this.parents.addAll(Arrays.asList(parents));
    }
  }

  /**
   * @return true if the 2 groups are conflicting, i.e. are distinct sibling groups
   */
  public boolean isDisparateTo(TaxGroup other) {
    if (this == other || other == null) {
      return false;
    }
    var cl1 = classification();
    cl1.add(this);
    var cl2 = other.classification();
    cl2.add(other);
    if (cl1.containsAll(cl2) || cl2.containsAll(cl1)) {
      return false;
    }
    return true;
  }

  /**
   * True if other group is contained in this group or is the same group,
   * i.e. the other groups classification contains this group.
   */
  public boolean contains(TaxGroup other) {
    if (this == other) {
      return true;
    }
    for (var p : other.parents) {
      if (this.contains(p)) return true;
    }
    return false;
  }

  public Set<TaxGroup> roots() {
    Set<TaxGroup> roots = new HashSet<>();
    if (parents.isEmpty()) {
      roots.add(this);
    } else {
      for (var p : parents) {
        roots.addAll(p.roots());
      }
    }
    return roots;
  }

  /**
   * @return set of all groups of the higher parent classification, excluding the group itself
   */
  public Set<TaxGroup> classification() {
    Set<TaxGroup> cl = new HashSet<>();
    for (var p : parents) {
      cl.add(p);
      cl.addAll(p.classification());
    }
    return cl;
  }

}
