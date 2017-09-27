package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import org.col.api.vocab.*;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public class Name {

  /**
   * Internal surrogate key of the name as provided by postgres.
   * This key is unique across all datasets but not exposed in the API.
   */
  @JsonIgnore
  private Integer key;

  /**
   * Primary key of the name as given in the dataset dwc:scientificNameID.
   * Only guaranteed to be unique within a dataset and can follow any kind of schema.
   */
  private String id;

  /**
   * Key to dataset instance. Defines context of the name key.
   */
  private Dataset dataset;

  /**
   * Entire canonical name string with a rank marker for infragenerics and infraspecfics, but excluding the authorship.
   * For uninomials, e.g. families or names at higher ranks, this is just the uninomial.
   */
  private String scientificName;

  /**
   * full unparsed authorship of the name incl basionym and years
   */
  private String authorship;

  /**
   * rank of the name from enumeration above
   */
  //@JsonProperty("rankMarker")
  //@JsonSerialize(using=RankSerde.RankJsonSerializer.class)
  //@JsonDeserialize(using=RankSerde.RankJsonDeserializer.class)
  private Rank rank;

  private Origin origin;

  private NomCode nomenclaturalCode;

  /**
   * The genus part of a bi- or trinomial name. Not used for genus names which are represented by the scientificName alone.
   */
  private String genus;

  /**
   * The infrageneric epithet. Used only as the terminal epithet for names at infrageneric ranks, not for species
   */
  private String infragenericEpithet;

  private String specificEpithet;

  private String infraspecificEpithet;

  /**
   * The part of the name which is considered a hybrid;  see [GBIF](https://github.com/gbif/gbif-api/blob/master/src/main/java/org/gbif/api/vocabulary/NamePart.java#L24)
   */
  private NamePart notho;

  /**
   * list of basionym authors.
   */
  private List<String> originalAuthors = Lists.newArrayList();

  /**
   * Year of original name publication
   */
  private String originalYear;

  /**
   * list of authors excluding ex- authors
   */
  private List<String> combinationAuthors = Lists.newArrayList();

  /**
   * The year this combination was first published, usually the same as the publishedIn reference.
   * It is used for sorting names and ought to be populated even for botanical names which do not use it in the complete authorship string.
   */
  private String combinationYear;

  /**
   * Link to the original combination.
   * In case of [replacement names](https://en.wikipedia.org/wiki/Nomen_novum) it points back to the replaced synonym.
   */
  private Name originalName;

  /**
   * true if the type specimen of the name is a fossil
   */
  private Boolean fossil;

  /**
   * Current nomenclatural status of the name taking into account all known nomenclatural acts.
   */
  private NomStatus status;

  /**
   * The kind of name classified in broad catagories based on their syntactical structure
   */
  private NameType type;

  private URI sourceUrl;

  /**
   * notes for general remarks on the name, i.e. its nomenclature
   */
  private String remarks;

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public void setDataset(Dataset dataset) {
    this.dataset = dataset;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public Origin getOrigin() {
    return origin;
  }

  public void setOrigin(Origin origin) {
    this.origin = origin;
  }

  public NomCode getNomenclaturalCode() {
    return nomenclaturalCode;
  }

  public void setNomenclaturalCode(NomCode nomenclaturalCode) {
    this.nomenclaturalCode = nomenclaturalCode;
  }

  public String getGenus() {
    return genus;
  }

  public void setGenus(String genus) {
    this.genus = genus;
  }

  public String getInfragenericEpithet() {
    return infragenericEpithet;
  }

  public void setInfragenericEpithet(String infragenericEpithet) {
    this.infragenericEpithet = infragenericEpithet;
  }

  public String getSpecificEpithet() {
    return specificEpithet;
  }

  public void setSpecificEpithet(String specificEpithet) {
    this.specificEpithet = specificEpithet;
  }

  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }

  public void setInfraspecificEpithet(String infraspecificEpithet) {
    this.infraspecificEpithet = infraspecificEpithet;
  }

  public NamePart getNotho() {
    return notho;
  }

  public void setNotho(NamePart notho) {
    this.notho = notho;
  }

  public List<String> getOriginalAuthors() {
    return originalAuthors;
  }

  public void setOriginalAuthors(List<String> originalAuthors) {
    this.originalAuthors = originalAuthors;
  }

  public String getOriginalYear() {
    return originalYear;
  }

  public void setOriginalYear(String originalYear) {
    this.originalYear = originalYear;
  }

  public List<String> getCombinationAuthors() {
    return combinationAuthors;
  }

  public void setCombinationAuthors(List<String> combinationAuthors) {
    this.combinationAuthors = combinationAuthors;
  }

  public String getCombinationYear() {
    return combinationYear;
  }

  public void setCombinationYear(String combinationYear) {
    this.combinationYear = combinationYear;
  }

  public Name getOriginalName() {
    return originalName;
  }

  public void setOriginalName(Name originalName) {
    this.originalName = originalName;
  }

  public Boolean getFossil() {
    return fossil;
  }

  public void setFossil(Boolean fossil) {
    this.fossil = fossil;
  }

  public NomStatus getStatus() {
    return status;
  }

  public void setStatus(NomStatus status) {
    this.status = status;
  }

  public NameType getType() {
    return type;
  }

  public void setType(NameType type) {
    this.type = type;
  }

  public URI getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(URI sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Name name = (Name) o;
    return Objects.equals(key, name.key) &&
        Objects.equals(id, name.id) &&
        Objects.equals(dataset, name.dataset) &&
        Objects.equals(scientificName, name.scientificName) &&
        Objects.equals(authorship, name.authorship) &&
        rank == name.rank &&
        origin == name.origin &&
        nomenclaturalCode == name.nomenclaturalCode &&
        Objects.equals(genus, name.genus) &&
        Objects.equals(infragenericEpithet, name.infragenericEpithet) &&
        Objects.equals(specificEpithet, name.specificEpithet) &&
        Objects.equals(infraspecificEpithet, name.infraspecificEpithet) &&
        notho == name.notho &&
        Objects.equals(originalAuthors, name.originalAuthors) &&
        Objects.equals(originalYear, name.originalYear) &&
        Objects.equals(combinationAuthors, name.combinationAuthors) &&
        Objects.equals(combinationYear, name.combinationYear) &&
        Objects.equals(originalName, name.originalName) &&
        Objects.equals(fossil, name.fossil) &&
        status == name.status &&
        type == name.type &&
        Objects.equals(sourceUrl, name.sourceUrl) &&
        Objects.equals(remarks, name.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, dataset, scientificName, authorship, rank, origin, nomenclaturalCode, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, notho, originalAuthors, originalYear, combinationAuthors, combinationYear, originalName, fossil, status, type, sourceUrl, remarks);
  }
}
