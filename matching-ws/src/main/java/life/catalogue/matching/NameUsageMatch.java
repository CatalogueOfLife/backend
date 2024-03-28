package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import javax.annotation.Nullable;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

public class NameUsageMatch implements LinneanClassification {

  private String usageKey;
  private String acceptedUsageKey;
  private String scientificName;
  private String canonicalName;
  private Rank rank;
  private TaxonomicStatus status;
  private Integer confidence;
  private String note;
  private MatchType matchType;
  private List<NameUsageMatch> alternatives;
  private String kingdom;
  private String phylum;

  @JsonProperty("class")
  private String clazz;

  private String order;
  private String family;
  private String genus;
  private String subgenus;
  private String species;
  private String kingdomKey;
  private String phylumKey;
  private String classKey;
  private String orderKey;
  private String familyKey;
  private String genusKey;
  private String subgenusKey;
  private String speciesKey;

  public NameUsageMatch() {
    this.matchType = MatchType.NONE;
  }

  @Schema(
      description =
          "The confidence that the lookup was correct.\n\nA value between 0 and 100 with higher values being better matches.")
  public @Min(0L) @Max(100L) Integer getConfidence() {
    return this.confidence;
  }

  public void setConfidence(Integer confidence) {
    this.confidence = confidence;
  }

  @Schema(description = "The type of match for this result.")
  public MatchType getMatchType() {
    return this.matchType;
  }

  public void setMatchType(MatchType matchType) {
    this.matchType = matchType;
  }

  @Schema(description = "The rank of the matching usage.")
  @Nullable
  public Rank getRank() {
    return this.rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  @Schema(description = "The scientific name of the matched name usage.")
  @Nullable
  public String getScientificName() {
    return this.scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  @Schema(description = "The name usage key of the name usage that has been matched.")
  @Nullable
  public String getUsageKey() {
    return this.usageKey;
  }

  public void setUsageKey(String usageKey) {
    this.usageKey = usageKey;
  }

  @Schema(
      description = "The key of the accepted name usage in case the matched usage was a synonym.")
  @Nullable
  public String getAcceptedUsageKey() {
    return this.acceptedUsageKey;
  }

  public void setAcceptedUsageKey(String acceptedUsageKey) {
    this.acceptedUsageKey = acceptedUsageKey;
  }

  @Schema(description = "True if the match name is a synonym.")
  public boolean isSynonym() {
    return this.status != null && this.status.isSynonym();
  }

  @Schema(description = "The taxonomic status of the backbone usage.")
  public TaxonomicStatus getStatus() {
    return this.status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  @Schema(description = "Matched name's kingdom.")
  @Nullable
  public String getKingdom() {
    return this.kingdom;
  }

  public void setKingdom(String kingdom) {
    this.kingdom = kingdom;
  }

  @Schema(description = "Matched name's phylum.")
  @Nullable
  public String getPhylum() {
    return this.phylum;
  }

  public void setPhylum(String phylum) {
    this.phylum = phylum;
  }

  @Schema(description = "Matched name's class.")
  @Nullable
  public String getClazz() {
    return this.clazz;
  }

  public void setClazz(String clazz) {
    this.clazz = clazz;
  }

  @Schema(description = "Matched name's order.")
  @Nullable
  public String getOrder() {
    return this.order;
  }

  public void setOrder(String order) {
    this.order = order;
  }

  @Schema(description = "Matched name's family.")
  @Nullable
  public String getFamily() {
    return this.family;
  }

  public void setFamily(String family) {
    this.family = family;
  }

  @Schema(description = "Matched name's genus.")
  @Nullable
  public String getGenus() {
    return this.genus;
  }

  public void setGenus(String genus) {
    this.genus = genus;
  }

  @Schema(description = "Matched name's subgenus.")
  @Nullable
  public String getSubgenus() {
    return this.subgenus;
  }

  public void setSubgenus(String subgenus) {
    this.subgenus = subgenus;
  }

  @Schema(description = "Matched name's species.")
  @Nullable
  public String getSpecies() {
    return this.species;
  }

  public void setSpecies(String species) {
    this.species = species;
  }

  @Schema(description = "Usage key of the kingdom of the matched name.")
  @Nullable
  public String getKingdomKey() {
    return this.kingdomKey;
  }

  public void setKingdomKey(String kingdomKey) {
    this.kingdomKey = kingdomKey;
  }

  @Schema(description = "Usage key of the phylum of the matched name.")
  @Nullable
  public String getPhylumKey() {
    return this.phylumKey;
  }

  public void setPhylumKey(String phylumKey) {
    this.phylumKey = phylumKey;
  }

  @Schema(description = "Usage key of the class of the matched name.")
  @Nullable
  public String getClassKey() {
    return this.classKey;
  }

  public void setClassKey(String classKey) {
    this.classKey = classKey;
  }

  @Schema(description = "Usage key of the order of the matched name.")
  @Nullable
  public String getOrderKey() {
    return this.orderKey;
  }

  public void setOrderKey(String orderKey) {
    this.orderKey = orderKey;
  }

  @Schema(description = "Usage key of the family of the matched name.")
  @Nullable
  public String getFamilyKey() {
    return this.familyKey;
  }

  public void setFamilyKey(String familyKey) {
    this.familyKey = familyKey;
  }

  @Schema(description = "Usage key of the genus of the matched name.")
  @Nullable
  public String getGenusKey() {
    return this.genusKey;
  }

  public void setGenusKey(String genusKey) {
    this.genusKey = genusKey;
  }

  @Schema(description = "Usage key of the subgenus of the matched name.")
  @Nullable
  public String getSubgenusKey() {
    return this.subgenusKey;
  }

  public void setSubgenusKey(String subgenusKey) {
    this.subgenusKey = subgenusKey;
  }

  @Schema(description = "Usage key of the species of the matched name.")
  @Nullable
  public String getSpeciesKey() {
    return this.speciesKey;
  }

  public void setSpeciesKey(String speciesKey) {
    this.speciesKey = speciesKey;
  }

  @Schema(description = "Usage key of the kingdom of the matched name.")
  @Nullable
  public String getHigherRank(Rank rank) {
    return this.getHigherRank(rank);
  }

  @Nullable
  public String getCanonicalName() {
    return this.canonicalName;
  }

  public void setCanonicalName(String canonicalName) {
    this.canonicalName = canonicalName;
  }

  @Nullable
  public String getNote() {
    return this.note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  @Nullable
  public List<NameUsageMatch> getAlternatives() {
    return this.alternatives;
  }

  public void setAlternatives(List<NameUsageMatch> alternatives) {
    this.alternatives = alternatives;
  }

  void setHigherRank(String key, String name, Rank rank) {
    if (rank != null) {
      switch (rank) {
        case KINGDOM:
          setKingdom(name);
          setKingdomKey(key);
          break;
        case PHYLUM:
          setPhylum(name);
          setPhylumKey(key);
          break;
        case CLASS:
          setClazz(name);
          setClassKey(key);
          break;
        case ORDER:
          setOrder(name);
          setOrderKey(key);
          break;
        case FAMILY:
          setFamily(name);
          setFamilyKey(key);
          break;
        case GENUS:
          setGenus(name);
          setGenusKey(key);
          break;
        case SUBGENUS:
          setSubgenus(name);
          setSubgenusKey(key);
          break;
        case SPECIES:
          setSpecies(name);
          setSpeciesKey(key);
      }
    }
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o != null && this.getClass() == o.getClass()) {
      NameUsageMatch that = (NameUsageMatch) o;
      return Objects.equals(this.usageKey, that.usageKey)
          && Objects.equals(this.acceptedUsageKey, that.acceptedUsageKey)
          && Objects.equals(this.scientificName, that.scientificName)
          && Objects.equals(this.canonicalName, that.canonicalName)
          && this.rank == that.rank
          && this.status == that.status
          && Objects.equals(this.confidence, that.confidence)
          && Objects.equals(this.note, that.note)
          && this.matchType == that.matchType
          && Objects.equals(this.alternatives, that.alternatives)
          && Objects.equals(this.kingdom, that.kingdom)
          && Objects.equals(this.phylum, that.phylum)
          && Objects.equals(this.clazz, that.clazz)
          && Objects.equals(this.order, that.order)
          && Objects.equals(this.family, that.family)
          && Objects.equals(this.genus, that.genus)
          && Objects.equals(this.subgenus, that.subgenus)
          && Objects.equals(this.species, that.species)
          && Objects.equals(this.kingdomKey, that.kingdomKey)
          && Objects.equals(this.phylumKey, that.phylumKey)
          && Objects.equals(this.classKey, that.classKey)
          && Objects.equals(this.orderKey, that.orderKey)
          && Objects.equals(this.familyKey, that.familyKey)
          && Objects.equals(this.genusKey, that.genusKey)
          && Objects.equals(this.subgenusKey, that.subgenusKey)
          && Objects.equals(this.speciesKey, that.speciesKey);
    } else {
      return false;
    }
  }

  public int hashCode() {
    return Objects.hash(
        new Object[] {
          this.usageKey,
          this.acceptedUsageKey,
          this.scientificName,
          this.canonicalName,
          this.rank,
          this.status,
          this.confidence,
          this.note,
          this.matchType,
          this.alternatives,
          this.kingdom,
          this.phylum,
          this.clazz,
          this.order,
          this.family,
          this.genus,
          this.subgenus,
          this.species,
          this.kingdomKey,
          this.phylumKey,
          this.classKey,
          this.orderKey,
          this.familyKey,
          this.genusKey,
          this.subgenusKey,
          this.speciesKey
        });
  }

  public String toString() {
    return (new StringJoiner(", ", NameUsageMatch.class.getSimpleName() + "[", "]"))
        .add("usageKey=" + this.usageKey)
        .add("acceptedUsageKey=" + this.acceptedUsageKey)
        .add("scientificName='" + this.scientificName + "'")
        .add("canonicalName='" + this.canonicalName + "'")
        .add("rank=" + this.rank)
        .add("status=" + this.status)
        .add("confidence=" + this.confidence)
        .add("note='" + this.note + "'")
        .add("matchType=" + this.matchType)
        .add("alternatives=" + this.alternatives)
        .add("kingdom='" + this.kingdom + "'")
        .add("phylum='" + this.phylum + "'")
        .add("clazz='" + this.clazz + "'")
        .add("order='" + this.order + "'")
        .add("family='" + this.family + "'")
        .add("genus='" + this.genus + "'")
        .add("subgenus='" + this.subgenus + "'")
        .add("species='" + this.species + "'")
        .add("kingdomKey=" + this.kingdomKey)
        .add("phylumKey=" + this.phylumKey)
        .add("classKey=" + this.classKey)
        .add("orderKey=" + this.orderKey)
        .add("familyKey=" + this.familyKey)
        .add("genusKey=" + this.genusKey)
        .add("subgenusKey=" + this.subgenusKey)
        .add("speciesKey=" + this.speciesKey)
        .toString();
  }

  public String getHigherRankKey(Rank r) {
    if (rank != null) {
      switch (rank) {
        case KINGDOM:
          return getKingdomKey();
        case PHYLUM:
          return getPhylumKey();
        case CLASS:
          return getClassKey();
        case ORDER:
          return getOrderKey();
        case FAMILY:
          return getFamilyKey();
        case GENUS:
          return getGenusKey();
        case SUBGENUS:
          return getSubgenusKey();
        case SPECIES:
          return getSpeciesKey();
      }
    }
    return null;
  }

  public enum MatchType {
    EXACT,
    FUZZY,
    HIGHERRANK,
    AGGREGATE,
    NONE;

    private MatchType() {}
  }
}
