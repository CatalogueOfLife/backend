package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import life.catalogue.api.vocab.TaxonomicStatus;
import lombok.Data;
import org.gbif.nameparser.api.Rank;

/**
 * A name usage match with additional classification information and a flag indicating if the name
 * is a synonym or not.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class NameUsageMatch implements LinneanClassification {

  boolean synonym = false;
  RankedName usage;
  @JsonIgnore TaxonomicStatus status;
  RankedName acceptedUsage;
  List<RankedName> classification = new ArrayList<>();
  List<NameUsageMatch> alternatives = new ArrayList<>();
  Diagnostics diagnostics = new Diagnostics();

  private String nameFor(Rank rank) {
    return getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(c -> c.getName())
        .orElse(null);
  }

  private String keyFor(Rank rank) {
    return getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(c -> c.getKey())
        .orElse(null);
  }

  private void setNameFor(String value, Rank rank) {
    Optional<RankedName> name =
        this.getClassification().stream().filter(c -> c.getRank().equals(rank)).findFirst();
    if (name.isPresent()) {
      name.get().setName(value);
      name.get().setCanonicalName(value);
    } else {
      RankedName newRank = new RankedName();
      newRank.setRank(rank);
      newRank.setName(value);
      newRank.setCanonicalName(value);
      this.getClassification().add(newRank);
    }
  }

  private void setKeyFor(String key, Rank rank) {
    Optional<RankedName> name =
        this.getClassification().stream().filter(c -> c.getRank().equals(rank)).findFirst();
    if (name.isPresent()) {
      name.get().setName(key);
    } else {
      RankedName newRank = new RankedName();
      newRank.setRank(rank);
      newRank.setKey(key);
      this.getClassification().add(newRank);
    }
  }

  public String getHigherRankKey(Rank rank) {
    return this.getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(c -> c.getKey())
        .orElse(null);
  }

  @Override
  @JsonIgnore
  public String getKingdom() {
    return nameFor(Rank.KINGDOM);
  }

  @Override
  @JsonIgnore
  public String getPhylum() {
    return nameFor(Rank.PHYLUM);
  }

  @Override
  @JsonIgnore
  public String getClazz() {
    return nameFor(Rank.CLASS);
  }

  @Override
  @JsonIgnore
  public String getOrder() {
    return nameFor(Rank.ORDER);
  }

  @Override
  @JsonIgnore
  public String getFamily() {
    return nameFor(Rank.FAMILY);
  }

  @Override
  @JsonIgnore
  public String getGenus() {
    return nameFor(Rank.GENUS);
  }

  @Override
  @JsonIgnore
  public String getSubgenus() {
    return nameFor(Rank.SUBGENUS);
  }

  @Override
  @JsonIgnore
  public String getSpecies() {
    return nameFor(Rank.SPECIES);
  }

  @JsonIgnore
  public String getKingdomKey() {
    return keyFor(Rank.KINGDOM);
  }

  @JsonIgnore
  public String getPhylumKey() {
    return keyFor(Rank.PHYLUM);
  }

  @JsonIgnore
  public String getClassKey() {
    return keyFor(Rank.CLASS);
  }

  @JsonIgnore
  public String getOrderKey() {
    return keyFor(Rank.ORDER);
  }

  @JsonIgnore
  public String getFamilyKey() {
    return keyFor(Rank.FAMILY);
  }

  @JsonIgnore
  public String getGenusKey() {
    return keyFor(Rank.GENUS);
  }

  @JsonIgnore
  public String getSubgenusKey() {
    return keyFor(Rank.SUBGENUS);
  }

  @JsonIgnore
  public String getSpeciesKey() {
    return keyFor(Rank.SPECIES);
  }

  @Override
  public void setKingdom(String v) {
    setNameFor(v, Rank.KINGDOM);
  }

  @Override
  public void setPhylum(String v) {
    setNameFor(v, Rank.PHYLUM);
  }

  @Override
  public void setClazz(String v) {
    setNameFor(v, Rank.CLASS);
  }

  @Override
  public void setOrder(String v) {
    setNameFor(v, Rank.ORDER);
  }

  @Override
  public void setFamily(String v) {
    setNameFor(v, Rank.FAMILY);
  }

  @Override
  public void setGenus(String v) {
    setNameFor(v, Rank.GENUS);
  }

  @Override
  public void setSubgenus(String v) {
    setNameFor(v, Rank.SUBGENUS);
  }

  @Override
  public void setSpecies(String v) {
    setNameFor(v, Rank.SPECIES);
  }

  @JsonIgnore
  public void setKingdomKey(String v) {
    setKeyFor(v, Rank.KINGDOM);
  }

  @JsonIgnore
  public void setPhylumKey(String v) {
    setKeyFor(v, Rank.PHYLUM);
  }

  @JsonIgnore
  public void setClassKey(String v) {
    setKeyFor(v, Rank.CLASS);
  }

  @JsonIgnore
  public void setOrderKey(String v) {
    setKeyFor(v, Rank.ORDER);
  }

  @JsonIgnore
  public void setFamilyKey(String v) {
    setKeyFor(v, Rank.FAMILY);
  }

  @JsonIgnore
  public void setGenusKey(String v) {
    setKeyFor(v, Rank.GENUS);
  }

  @JsonIgnore
  public void setSubgenusKey(String v) {
    setKeyFor(v, Rank.SUBGENUS);
  }

  @JsonIgnore
  public void setSpeciesKey(String v) {
    setKeyFor(v, Rank.SPECIES);
  }
}
