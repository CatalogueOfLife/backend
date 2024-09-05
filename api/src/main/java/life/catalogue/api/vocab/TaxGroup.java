package life.catalogue.api.vocab;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NomCode;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Informal and common broad grouping of large taxonomic groups.
 * These groups often are paraphyletic, but are convenient for broad classifications
 * and match better the application of the nomenclatural codes, e.g. Algae, Fungi & Plants for the "botanical" code.
 * Links to https://www.phylopic.org
 */
public enum TaxGroup {

  Viruses("6dba8988-8a64-4c10-8934-77d5880dc21c", null, NomCode.VIRUS),
  Prokaryotes("680e6e45-3e3a-4e4b-b679-9bf2ae9091ab", null, NomCode.BACTERIAL),
    Bacteria("7bd82403-a662-41bd-9710-cf095bea5809", Prokaryotes),
    Archaea("d979c5bd-f760-427d-bd23-7b8779bd1c8f", Prokaryotes),

  Eukaryotes("d878c432-0b0b-44f5-b1a1-12557b439c3f", "",NomCode.ZOOLOGICAL, NomCode.BOTANICAL),
    Protists("5fccc7f9-60b8-4aea-96cf-ce93f85c9e90", "Any eukaryote that is not animal, plant (incl algae s.l.) nor fungus. " +
      "Includes all Protozoa (ciliates, flagellates, and amoebas)", Eukaryotes),

    Plants("5c6ae9a1-1405-4f81-b4c1-c169faef475e", "Sensu lato incl red algae. More like Archaeplastida", NomCode.BOTANICAL, Eukaryotes),

      Algae("65414989-e801-4d0e-acc1-4309cd35ab7e", "Sensu lato including names traditionally treated as eukaryotic algae. " +
        "Includes diatoms, red algae and any protist algae, but excluding prokaryotic cyanobacteria",
        Plants, Protists),
      Bryophytes("f4889f66-1904-479d-8a1f-f9449733c654", "sensu lato incl liverworts, hornworts and mosses", Plants),
      Pteridophytes("ee243ea1-c311-4fa2-b730-928b80117515", "sensu lato with fern allies incl clubmosses, horsetails and whisk ferns", Plants),
      Angiosperms("3e84c211-b5d1-4c4d-9749-9dbda2e73f89", "",Plants),
      Gymnosperms("9b9d6b61-d571-4503-9107-50c54a2beedb", "",Plants),

    Fungi("e5d32221-7ea9-46ed-8e0a-d9dbddab0b4a", "Includes lichen", NomCode.BOTANICAL, Eukaryotes),
      Ascomycetes("7ebbf05d-2084-4204-ad4c-2c0d6cbcdde1", Fungi),
      Basidiomycetes("237a0fb1-7b73-43ec-b0a5-eff95e7237df", Fungi),
      Oomycetes("53b543aa-2a5e-4407-88cd-6acbb0b5c3f9",
        "traditionally follows fungal nomenclature therefore placed here. Phylogenetically related to Algae and protists", Fungi),
      OtherFungi("", "",Fungi),

    Animals("a3dd3044-648c-4e45-93f0-21a156247132", "",NomCode.ZOOLOGICAL, Eukaryotes),
      Arthropods("83c995d5-0936-441b-8a27-089005086c61", "",Animals),
        Insects("b2e26249-80be-441b-8648-4c0361892ee0", "",Arthropods),
          Coleoptera("35bb269c-5ff2-48c1-a002-a6bf8d9e8eef", "",Insects),
          Diptera("fb360445-4c23-452e-b43d-34b42ce449dd", "",Insects),
          Lepidoptera("a36477e3-5e2c-4e28-94f8-90b397212c3b", "",Insects),
          Hymenoptera("b00b5230-a08a-4413-a9ed-f8b3e5f795b0", "",Insects),
          Hemiptera("61e306a9-9b19-4c9a-ba10-7856bd096356", "",Insects),
          Orthoptera("e7ba011c-6172-47ca-84bf-0dfc87b47a32", "",Insects),
          Trichoptera("e1307c88-3e8f-4ba8-9f93-751df3deb739", "",Insects),
          OtherInsects("", "",Insects),
        Arachnids("95908e68-8d10-4f48-91a2-b0d7d85c4c2d", "",Arthropods),
        Crustacean("a8dc4f0a-5b17-4ed4-bc4a-8af69db73c16", Arthropods),
        OtherArthropods("", Arthropods),
      Molluscs("e9ca8d1d-36cd-48bc-aa6e-8303be2b78f6", Animals),
        Gastropods("9da7781e-48eb-407d-a13f-d6de8954dde2", Molluscs),
        Bivalves("70d345ec-c53a-45b9-a4af-93cf6f3e2125", Molluscs),
        OtherMolluscs("", Molluscs),
      Chordates("aba20543-fb51-42d1-a03b-a9f9d25d526b", Animals),
        Amphibians("40b1198a-ec00-4dff-ab82-c79817244485", Chordates),
        Birds("ca1082e0-718c-48dc-a011-995511e48180", Chordates),
        Mammals("f4b6df56-f216-4a4c-9940-4105da8b462e", Chordates),
        Reptiles("aefc9af2-1f5d-4e16-94c4-da3436b8e92d", Chordates),
        Fish("23a7d09d-4a4d-4ad5-ad07-49a6b59a7fba", Chordates),
        OtherChordates("", Chordates),
      OtherAnimals("", Animals),

    OtherEukaryotes("", Eukaryotes);

  public enum SIZE {
    PX64(64), PX128(128), PX192(192);
    final int pixel;

    SIZE(int pixel) {
      this.pixel = pixel;
    }

    int pixel() {
      return pixel;
    }
  }

  final private String phylopic;
  final private String description;
  final Set<TaxGroup> parents = new HashSet<>();
  final Set<NomCode> codes = new HashSet<>();


  TaxGroup(String phylopic, TaxGroup... parents) {
    this(phylopic,null, parents);
  }
  TaxGroup(String phylopic, String description, TaxGroup... parents) {
    this.phylopic = StringUtils.trimToNull(phylopic);
    this.description = description;
    if (parents != null) {
      for (var p : parents) {
        this.parents.add(p);
        this.codes.addAll(p.codes);
      }
    }
  }

  TaxGroup(String phylopic, String description, NomCode code1, NomCode code2) {
    this.phylopic = StringUtils.trimToNull(phylopic);
    this.description = description;
    this.codes.add(code1);
    this.codes.add(code2);
  }
  TaxGroup(String phylopic, String description, NomCode code, TaxGroup... parents) {
    this.phylopic = StringUtils.trimToNull(phylopic);
    this.description = description;
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

  public boolean isOther() {
    return name().startsWith("Other");
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

  public URI getIcon() {
    return getIcon(TaxGroup.SIZE.PX64);
  }
  public URI getIcon(SIZE size) {
    return phylopic == null ? null : URI.create("https://images.phylopic.org/images/" + phylopic + "/thumbnail/"+size.pixel+"x"+size.pixel+".png");
  }
  public URI getIconSVG() {
    return phylopic == null ? null : URI.create("https://images.phylopic.org/images/" + phylopic + "/vector.svg");
  }

  public String getPhylopic() {
    return phylopic;
  }

  public String getDescription() {
    return description;
  }

  public Set<String> getParents() {
    return parents == null ? null : parents.stream().map(Enum::name).collect(Collectors.toSet());
  }

  public Set<NomCode> getCodes() {
    return codes;
  }
}
