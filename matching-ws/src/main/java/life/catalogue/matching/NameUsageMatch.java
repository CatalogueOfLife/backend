package life.catalogue.matching;

import life.catalogue.api.vocab.TaxonomicStatus;

import lombok.Data;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class NameUsageMatch implements LinneanClassification {

  boolean synonym = false;
  RankedName usage;
  TaxonomicStatus status;
  RankedName acceptedUsage;
  List<RankedName> classification = new ArrayList<>();
  List<NameUsageMatch> alternatives = new ArrayList<>();
  Diagnostics diagnostics = new Diagnostics();

  private String nameFor(Rank rank){
     return getClassification().stream().filter(c -> c.getRank().equals(rank)).findFirst().map(c -> c.getName()).orElse(null);
  }

  private void setNameFor(String value, Rank rank){
    Optional<RankedName> name = this.getClassification().stream().filter(c -> c.getRank().equals(rank)).findFirst();
    if(name.isPresent()){
      name.get().setName(value);
    } else {
      RankedName newRank = new RankedName();
      newRank.setRank(rank);
      newRank.setName(value);
      this.getClassification().add(newRank);
    }
  }

  public String getHigherRankKey(Rank rank){
    return this.getClassification().stream().filter(c -> c.getRank().equals(rank)).findFirst().map(c -> c.getKey()).orElse(null);
  }

  @Override
  public String getKingdom() {
    return nameFor(Rank.KINGDOM);
  }

  @Override
  public String getPhylum() {
    return nameFor(Rank.PHYLUM);
  }

  @Override
  public String getClazz() {
    return nameFor(Rank.CLASS);
  }

  @Override
  public String getOrder() {
    return nameFor(Rank.ORDER);
  }

  @Override
  public String getFamily() {
    return nameFor(Rank.FAMILY);
  }

  @Override
  public String getGenus() {
    return nameFor(Rank.GENUS);
  }

  @Override
  public String getSubgenus() {
    return nameFor(Rank.SUBGENUS);
  }

  @Override
  public String getSpecies() {
    return nameFor(Rank.SPECIES);
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
}




