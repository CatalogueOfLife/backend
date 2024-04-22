package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LinneanClassificationImpl implements LinneanClassification {
  String kingdom;
  String phylum;
  String clazz;
  String order;
  String family;
  String genus;
  String subgenus;
  String species;

  @Override
  public String getKingdom() {
    return kingdom;
  }

  @Override
  public void setKingdom(String kingdom) {
    this.kingdom = kingdom;
  }

  @Override
  public String getPhylum() {
    return phylum;
  }

  @Override
  public void setPhylum(String phylum) {
    this.phylum = phylum;
  }

  @Override
  public String getOrder() {
    return order;
  }

  @Override
  public void setOrder(String order) {
    this.order = order;
  }

  @Override
  public String getFamily() {
    return family;
  }

  @Override
  public void setFamily(String family) {
    this.family = family;
  }

  @Override
  public String getGenus() {
    return genus;
  }

  @Override
  public void setGenus(String genus) {
    this.genus = genus;
  }

  @Override
  public String getSubgenus() {
    return subgenus;
  }

  @Override
  public void setSubgenus(String subgenus) {
    this.subgenus = subgenus;
  }

  @Override
  public String getSpecies() {
    return species;
  }

  @Override
  public void setSpecies(String species) {
    this.species = species;
  }

  @JsonProperty("class")
  @Override
  public String getClazz() {
    return clazz;
  }

  @JsonProperty("class")
  @Override
  public void setClazz(String clazz) {
    this.clazz = clazz;
  }
}
