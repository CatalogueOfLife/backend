package life.catalogue.db.mapper.legacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class LSpeciesName extends LHigherName {
  private String onlineResource;
  private String sourceDatabase;
  private String sourceDatabaseUrl;
  private String bibliographicCitation;
  private LScrutiny recordScrutinyDate;
  private String genus;
  private String subgenus;
  private String species;
  private String infraspeciesMarker;
  private String infraspecies;
  private String author;
  private String distribution;
  private List<LReference> references;
  private List<LHigherName> classification;
  private List<LSpeciesName> childTaxa;
  private List<LSpeciesName> synonyms;
  private List<LCommonName> commonNames;


  @Override
  public String getNameHtml() {
    String html = super.getNameHtml();
    if (author != null) {
      html = html + " " + author;
    }
    return html;
  }

  @JsonProperty("online_resource")
  public String getOnlineResource() {
    return onlineResource;
  }

  public void setOnlineResource(String onlineResource) {
    this.onlineResource = onlineResource;
  }

  @JsonProperty("source_database")
  public String getSourceDatabase() {
    return sourceDatabase;
  }

  public void setSourceDatabase(String sourceDatabase) {
    this.sourceDatabase = sourceDatabase;
  }

  @JsonProperty("source_database_url")
  public String getSourceDatabaseUrl() {
    return sourceDatabaseUrl;
  }

  public void setSourceDatabaseUrl(String sourceDatabaseUrl) {
    this.sourceDatabaseUrl = sourceDatabaseUrl;
  }

  @JsonProperty("record_scrutiny_date")
  public LScrutiny getRecordScrutinyDate() {
    return recordScrutinyDate;
  }

  public void setRecordScrutinyDate(LScrutiny recordScrutinyDate) {
    this.recordScrutinyDate = recordScrutinyDate;
  }

  @JsonProperty("bibliographic_citation")
  public String getBibliographicCitation() {
    return bibliographicCitation;
  }

  public void setBibliographicCitation(String bibliographicCitation) {
    this.bibliographicCitation = bibliographicCitation;
  }

  public String getGenus() {
    return genus;
  }

  public void setGenus(String genus) {
    this.genus = genus;
  }

  public String getSubgenus() {
    return subgenus;
  }

  public void setSubgenus(String subgenus) {
    this.subgenus = subgenus;
  }

  public String getSpecies() {
    return species;
  }

  public void setSpecies(String species) {
    this.species = species;
  }

  public String getInfraspeciesMarker() {
    return infraspeciesMarker;
  }

  public void setInfraspeciesMarker(String infraspeciesMarker) {
    this.infraspeciesMarker = infraspeciesMarker;
  }

  public String getInfraspecies() {
    return infraspecies;
  }

  public void setInfraspecies(String infraspecies) {
    this.infraspecies = infraspecies;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getDistribution() {
    return distribution;
  }

  public void setDistribution(String distribution) {
    this.distribution = distribution;
  }

  public List<LReference> getReferences() {
    return references;
  }

  public void setReferences(List<LReference> references) {
    this.references = references;
  }

  public List<LHigherName> getClassification() {
    return classification;
  }

  public void setClassification(List<LHigherName> classification) {
    this.classification = classification;
  }

  public List<LSpeciesName> getChildTaxa() {
    return childTaxa;
  }

  public void setChildTaxa(List<LSpeciesName> childTaxa) {
    this.childTaxa = childTaxa;
  }

  public List<LSpeciesName> getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(List<LSpeciesName> synonyms) {
    this.synonyms = synonyms;
  }

  public List<LCommonName> getCommonNames() {
    return commonNames;
  }

  public void setCommonNames(List<LCommonName> commonNames) {
    this.commonNames = commonNames;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LSpeciesName)) return false;
    if (!super.equals(o)) return false;
    LSpeciesName that = (LSpeciesName) o;
    return Objects.equals(onlineResource, that.onlineResource) &&
      Objects.equals(sourceDatabase, that.sourceDatabase) &&
      Objects.equals(sourceDatabaseUrl, that.sourceDatabaseUrl) &&
      Objects.equals(bibliographicCitation, that.bibliographicCitation) &&
      Objects.equals(recordScrutinyDate, that.recordScrutinyDate) &&
      Objects.equals(genus, that.genus) &&
      Objects.equals(subgenus, that.subgenus) &&
      Objects.equals(species, that.species) &&
      Objects.equals(infraspeciesMarker, that.infraspeciesMarker) &&
      Objects.equals(infraspecies, that.infraspecies) &&
      Objects.equals(author, that.author) &&
      Objects.equals(distribution, that.distribution) &&
      Objects.equals(references, that.references) &&
      Objects.equals(classification, that.classification) &&
      Objects.equals(childTaxa, that.childTaxa) &&
      Objects.equals(synonyms, that.synonyms) &&
      Objects.equals(commonNames, that.commonNames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), onlineResource, sourceDatabase, sourceDatabaseUrl, bibliographicCitation, recordScrutinyDate, genus, subgenus, species, infraspeciesMarker, infraspecies, author, distribution, references, classification, childTaxa, synonyms, commonNames);
  }
}
