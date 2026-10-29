package life.catalogue.api.vocab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Informal and common broad grouping of large taxonomic groups (usually > 50.000 species).
 * These groups often are paraphyletic, but are convenient for broad classifications
 * and match better the application of the nomenclatural codes, e.g. Algae, Fungi & Plants for the "botanical" code.
 *
 * Every group has an icon served by the ChecklistBank API, built from public domain https://www.phylopic.org silhouettes
 * by vocab/icons/build-icons.py. The icons.tsv next to the icons lists the images and the taxa they depict
 * and must agree with the phylopic uuid kept here.
 * Composite icons made of several silhouettes (Eukaryotes, Animals, Chordates) have no single phylopic uuid.
 */
public enum TaxGroup {

  Viruses("6dba8988-8a64-4c10-8934-77d5880dc21c", "Viruses", NomCode.VIRUS),
  Prokaryotes("0698c39f-9ab0-44e0-ba70-2faefaf22216", "Prokaryota", NomCode.BACTERIAL),
    Bacteria("7bd82403-a662-41bd-9710-cf095bea5809", "Bacteria including cyanobacteria", Prokaryotes),
    Archaea("d979c5bd-f760-427d-bd23-7b8779bd1c8f", "Archaea excluding the eukaryotes", Prokaryotes),

  Eukaryotes(null, "Eukaryotes are divided into 4 groups only similar to Haeckels 4 kingdoms",
    NomCode.ZOOLOGICAL, NomCode.BOTANICAL),
    Protists("5fccc7f9-60b8-4aea-96cf-ce93f85c9e90", "Any eukaryote that is not animal, plant (incl algae s.l.) nor fungus. " +
      "Includes all Protozoa (ciliates, flagellates, and amoebas), Foraminifera, Radiolaria", Eukaryotes),

    Plants("5c6ae9a1-1405-4f81-b4c1-c169faef475e", "Sensu lato incl red algae and Glaucophytes. Similar to Archaeplastida", NomCode.BOTANICAL, Eukaryotes),

      Algae("2bb3f2a4-1567-4a8e-bf33-16abb7f0c7c1", "Sensu lato including names traditionally treated as eukaryotic algae. " +
        "Includes diatoms, red algae and any protist algae, but excluding prokaryotic cyanobacteria",
        Plants, Protists),
      Bryophytes("f4889f66-1904-479d-8a1f-f9449733c654", "sensu lato incl liverworts, hornworts and mosses", Plants),
      Pteridophytes("ee243ea1-c311-4fa2-b730-928b80117515", "sensu lato with fern allies incl clubmosses, horsetails and whisk ferns", Plants),
      Angiosperms("cc9f5826-afd9-480e-ad22-c9311de167c5", "Flowering plants, Magnoliophyta", Plants),
      Gymnosperms("9b9d6b61-d571-4503-9107-50c54a2beedb", "Seed plants including Cycads, Ginkgo, Gnetophytes and Conifers", Plants),

    Fungi("e5d32221-7ea9-46ed-8e0a-d9dbddab0b4a", "Includes lichen", NomCode.BOTANICAL, Eukaryotes),
      Ascomycetes("7ebbf05d-2084-4204-ad4c-2c0d6cbcdde1", "Ascomycota", Fungi),
      Basidiomycetes("237a0fb1-7b73-43ec-b0a5-eff95e7237df", "Basidiomycota", Fungi),
      Pseudofungi("53b543aa-2a5e-4407-88cd-6acbb0b5c3f9",
        "traditionally follows fungal nomenclature therefore placed here. Includes Oomycetes and Hyphochytriomycetes. Phylogenetically related to Algae and protists", Fungi, Algae),
      OtherFungi("44c27c08-400f-4304-92b4-997fdd85ec4b", "All other basal fungi not being Asco- or Basidiomycetes, e.g. Chytridiomycota (chytrids), Zygomycota (bread molds) and Glomeromycota (arbuscular mycorrhizal fungi)",Fungi),

    Animals(null, "Animalia",NomCode.ZOOLOGICAL, Eukaryotes),
      Arthropods("83c995d5-0936-441b-8a27-089005086c61", "Arthropoda",Animals),
        Insects("b2e26249-80be-441b-8648-4c0361892ee0", "Insecta, the most diverse animal group",Arthropods),
          Coleoptera("35bb269c-5ff2-48c1-a002-a6bf8d9e8eef", "Beetles, e.g. Curculionidae (weevils), Carabidae (ground beetles) and Scarabaeidae (scarabs)",Insects),
          Diptera("c5564abf-a920-4ee3-a6a1-02c4eca1a671", "True flies incl Culicidae (mosquitoes), Tipulidae (crane flies) and Chironomidae (midges)",Insects),
          Lepidoptera("a36477e3-5e2c-4e28-94f8-90b397212c3b", "Butterflies and moths, e.g. Papilionoidea (butterflies), Sphingidae (hawk moths) and Noctuidae (owlet moths)",Insects),
          Hymenoptera("b00b5230-a08a-4413-a9ed-f8b3e5f795b0", "Formicidae (ants), Anthophila (bees), Vespidae (wasps) and Symphyta (sawflies)",Insects),
          Hemiptera("61e306a9-9b19-4c9a-ba10-7856bd096356", "True bugs incl Aphididae (aphids), Cicadidae (cicadas) and Pentatomidae (shield bugs)",Insects),
          Orthoptera("e7ba011c-6172-47ca-84bf-0dfc87b47a32", "Caelifera (grasshoppers and locusts) and Ensifera (crickets and katydids)",Insects),
          Trichoptera("e1307c88-3e8f-4ba8-9f93-751df3deb739", "Caddisflies, e.g. Hydropsychidae (net-spinning caddisflies) and Limnephilidae (northern caddisflies)",Insects),
          OtherInsects("2894776f-a2cf-4475-b13d-6d8ea1607ea4", "All other insects, e.g. Odonata (dragonflies), Blattodea (cockroaches and termites), Ephemeroptera (mayflies), Dermaptera (earwigs) and Neuroptera (lacewings)",Insects),
        Arachnids("95908e68-8d10-4f48-91a2-b0d7d85c4c2d", "Arachnida incl Araneae (spiders), Scorpiones (scorpions) and Acari (mites and ticks)",Arthropods),
        Crustacean("a8dc4f0a-5b17-4ed4-bc4a-8af69db73c16", "Crustacea incl Decapoda (crabs, lobsters and shrimps), Cirripedia (barnacles) and Copepoda (copepods)", Arthropods),
        OtherArthropods("1d2c7cb7-4a62-4f4a-96ff-b62e225d6f91", "All other arthropods, e.g. Myriapoda (centipedes and millipedes) and Pycnogonida (sea spiders)", Arthropods),
      Molluscs("e9ca8d1d-36cd-48bc-aa6e-8303be2b78f6", "Mollusca, soft-bodied invertebrates incl Gastropoda (snails), Bivalvia (clams) and Cephalopoda (squids and octopuses)", Animals),
        Gastropods("d8f236a7-e36b-4313-9162-8f2dead31a8a", "Gastropoda, snails and slugs, incl Caenogastropoda (sea snails), Stylommatophora (land snails and slugs) and Nudibranchia (sea slugs)", Molluscs),
        Bivalves("70d345ec-c53a-45b9-a4af-93cf6f3e2125", "Bivalvia incl Ostreidae (oysters), Mytilidae (mussels), Pectinidae (scallops) and Veneridae (clams)", Molluscs),
        OtherMolluscs("f060bcc7-5725-46f7-8276-14553af3707f", "All other molluscs, e.g. Cephalopoda (squids and octopuses), Polyplacophora (chitons) and Scaphopoda (tusk shells)", Molluscs),
      Chordates(null, "Chordata incl vertebrates such as Actinopterygii (ray-finned fishes), Amphibia (amphibians), Reptilia (reptiles), Aves (birds) and Mammalia (mammals)", Animals),
      OtherAnimals("f0d839a1-5a3e-42b2-828a-c5e536f0f7b7", "All other animals, e.g. Cnidaria (jellyfish and corals), Echinodermata (starfish and sea urchins), Annelida (segmented worms), Nematoda (roundworms), Platyhelminthes (flatworms) and Porifera (sponges)", Animals);

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

  /**
   * Disparate groups whose names are not kept apart as strictly, as the same nomenclatural code governs both sides and
   * the kingdom placement of their taxa changed a lot: protozoa used to be animals, slime moulds and other protists were
   * treated as fungi, and fungi as plants. Data still files plankton under Animalia or lichens under Plantae.
   * Symmetric, see {@link #overlaps(TaxGroup)}.
   */
  private static final Map<TaxGroup, Set<TaxGroup>> OVERLAPS = new EnumMap<>(TaxGroup.class);
  static {
    overlap(Protists, Animals);
    overlap(Protists, Fungi);
    overlap(Fungi, Plants);
  }

  private static void overlap(TaxGroup g1, TaxGroup g2) {
    OVERLAPS.computeIfAbsent(g1, k -> EnumSet.noneOf(TaxGroup.class)).add(g2);
    OVERLAPS.computeIfAbsent(g2, k -> EnumSet.noneOf(TaxGroup.class)).add(g1);
  }

  private static final String ICON_API = "https://api.checklistbank.org/vocab/taxgroup/";
  private static final String ICON_RESOURCES = "/life/catalogue/api/vocab/taxgroup/";

  final private String phylopic;
  final private String description;
  final LinkedHashSet<TaxGroup> parents = new LinkedHashSet<>();
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
   * True if the two disparate groups overlap, so a conflict between them is a weaker one. That is the case if one of the
   * two groups is itself declared to overlap with the other one or a group containing it: Animals overlap with Protists
   * and with Algae below them, Insects with Protists, but Insects never with Algae - two more specific groups contradict.
   * Groups that are not disparate do not overlap, they agree.
   */
  public boolean overlaps(TaxGroup other) {
    return other != null && isDisparateTo(other) && (overlapsPartner(this, other) || overlapsPartner(other, this));
  }

  private static boolean overlapsPartner(TaxGroup declared, TaxGroup other) {
    for (var partner : OVERLAPS.getOrDefault(declared, Set.of())) {
      if (partner.contains(other)) {
        return true;
      }
    }
    return false;
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
    return URI.create(ICON_API + name() + "/icon/" + size.pixel + ".png");
  }
  public URI getIconSVG() {
    return URI.create(ICON_API + name() + "/icon.svg");
  }

  /**
   * @param size the png size or null for the svg icon
   * @return the classpath resource of the icon
   */
  public URL iconResource(@Nullable SIZE size) {
    return TaxGroup.class.getResource(ICON_RESOURCES + name() + (size == null ? ".svg" : "-" + size.pixel + ".png"));
  }

  /**
   * @return the png icon size with the given pixel width, or null if there is none
   */
  public static SIZE size(int pixel) {
    for (var s : SIZE.values()) {
      if (s.pixel == pixel) return s;
    }
    return null;
  }

  public String getPhylopic() {
    return phylopic;
  }

  public String getDescription() {
    return description;
  }

  public Set<String> getParents() {
    return parents.isEmpty() ? null : parents.stream().map(Enum::name).collect(Collectors.toSet());
  }

  /**
   * @return the groups this group is declared to overlap with, see {@link #overlaps(TaxGroup)}
   */
  public Set<String> getOverlaps() {
    var o = OVERLAPS.get(this);
    return o == null ? null : o.stream().map(Enum::name).collect(Collectors.toSet());
  }

  @JsonIgnore
  public TaxGroup getPrimaryParent() {
    return parents.isEmpty() ? null : parents.iterator().next();
  }

  /**
   * @return scientific names of the organisms the icon depicts, several for a composite icon
   */
  public List<String> getIconTaxa() {
    return IconSources.SOURCES.get(this).taxa;
  }

  /**
   * The PhyloPic images and depicted taxa of every icon as listed in icons.tsv next to the icons.
   * A holder class as enum constructors cannot use static fields of their own enum.
   */
  static class IconSources {
    final List<String> phylopics;
    final List<String> taxa;
    static final Map<TaxGroup, IconSources> SOURCES = load();

    private IconSources(List<String> phylopics, List<String> taxa) {
      this.phylopics = phylopics;
      this.taxa = taxa;
    }

    private static Map<TaxGroup, IconSources> load() {
      var sources = new EnumMap<TaxGroup, IconSources>(TaxGroup.class);
      try (var in = TaxGroup.class.getResourceAsStream(ICON_RESOURCES + "icons.tsv")) {
        var lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).collect(Collectors.toList());
        for (String line : lines) {
          var cols = line.split("\t");
          sources.put(TaxGroup.valueOf(cols[0]), new IconSources(List.of(cols[1].split(",")), List.of(cols[2].split(", "))));
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read TaxGroup icon sources", e);
      }
      return Collections.unmodifiableMap(sources);
    }
  }

  public Set<NomCode> getCodes() {
    return codes;
  }
}
