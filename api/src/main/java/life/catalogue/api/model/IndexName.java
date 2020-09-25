package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.jackson.IsEmptyFilter;
import org.gbif.nameparser.api.*;

import javax.annotation.Nonnull;
import java.util.Objects;

import static life.catalogue.common.tax.NameFormatter.HYBRID_MARKER;

/**
 * A parsed or unparsed name that belongs to the names index.
 * Contains all main Name properties but removes all dataset, verbatim, sector extras.
 */
public class IndexName extends DataEntity<Integer> implements LinneanName, ScientificName {

  @JsonProperty("id")
  private Integer key;
  private Integer canonicalId;
  @Nonnull
  private String scientificName;
  private String authorship;
  @Nonnull
  private Rank rank;
  private String uninomial;
  private String genus;
  private String infragenericEpithet;
  private String specificEpithet;
  private String infraspecificEpithet;
  private String cultivarEpithet;
  private boolean candidatus;
  private NamePart notho;
  @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IsEmptyFilter.class)
  private Authorship combinationAuthorship = new Authorship();
  @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IsEmptyFilter.class)
  private Authorship basionymAuthorship = new Authorship();
  private String sanctioningAuthor;
  private NomCode code;
  private NameType type;

  public IndexName() {
  }

  public IndexName(Name n) {
    this.scientificName = n.getScientificName();
    this.authorship = n.getAuthorship();
    this.rank = n.getRank();
    this.uninomial = n.getUninomial();
    this.genus = n.getGenus();
    this.infragenericEpithet = n.getInfragenericEpithet();
    this.specificEpithet = n.getSpecificEpithet();
    this.infraspecificEpithet = n.getInfraspecificEpithet();
    this.cultivarEpithet = n.getCultivarEpithet();
    this.candidatus = n.isCandidatus();
    this.notho = n.getNotho();
    this.combinationAuthorship = n.getCombinationAuthorship();
    this.basionymAuthorship = n.getBasionymAuthorship();
    this.sanctioningAuthor = n.getSanctioningAuthor();
    this.code = n.getCode();
    this.type = n.getType();
    this.setCreated(n.getCreated());
    this.setModified(n.getModified());
  }

  @Override
  public Integer getKey() {
    return key;
  }

  @Override
  public void setKey(Integer key) {
    this.key = key;
  }

  public Integer getCanonicalId() {
    return canonicalId;
  }

  public void setCanonicalId(Integer canonicalId) {
    this.canonicalId = canonicalId;
  }

  @Override
  public String getScientificName() {
    return scientificName;
  }
  
  /**
   * WARN: avoid setting the cached scientificName for parsed names directly.
   * Use updateNameCache() instead!
   */
  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }
  
  /**
   * Cached complete authorship
   */
  @Override
  public String getAuthorship() {
    return authorship;
  }

  /**
   * WARN: avoid setting the cached complete authorship for parsed names directly.
   * Use updateNameCache() instead!
   */
  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }
  
  @JsonIgnore
  public boolean hasCombinationAuthorship() {
    return combinationAuthorship != null && !combinationAuthorship.isEmpty();
  }

  public Authorship getCombinationAuthorship() {
    return combinationAuthorship;
  }
  
  public void setCombinationAuthorship(Authorship combinationAuthorship) {
    this.combinationAuthorship = combinationAuthorship;
  }

  @JsonIgnore
  public boolean hasBasionymAuthorship() {
    return basionymAuthorship != null && !basionymAuthorship.isEmpty();
  }

  public Authorship getBasionymAuthorship() {
    return basionymAuthorship;
  }
  
  public void setBasionymAuthorship(Authorship basionymAuthorship) {
    this.basionymAuthorship = basionymAuthorship;
  }
  
  public String getSanctioningAuthor() {
    return sanctioningAuthor;
  }
  
  public void setSanctioningAuthor(String sanctioningAuthor) {
    this.sanctioningAuthor = sanctioningAuthor;
  }
  
  public Rank getRank() {
    return rank;
  }
  
  public void setRank(Rank rank) {
    this.rank = rank == null ? Rank.UNRANKED : rank;
  }
  
  public NomCode getCode() {
    return code;
  }
  
  public void setCode(NomCode code) {
    this.code = code;
  }
  
  public String getUninomial() {
    return uninomial;
  }
  
  private boolean setNothoIfHybrid(String x, NamePart part) {
    boolean isHybrid = x != null && !x.isEmpty() && x.charAt(0) == HYBRID_MARKER;
    if (isHybrid) {
      notho = part;
    }
    return isHybrid;
  }
  
  public void setUninomial(String uni) {
    if (setNothoIfHybrid(uni, NamePart.GENERIC)) {
      this.uninomial = uni.substring(1);
    } else {
      this.uninomial = uni;
    }
  }
  
  public String getGenus() {
    return genus;
  }
  
  public void setGenus(String genus) {
    if (setNothoIfHybrid(genus, NamePart.GENERIC)) {
      this.genus = genus.substring(1);
    } else {
      this.genus = genus;
    }
  }
  
  public String getInfragenericEpithet() {
    return infragenericEpithet;
  }
  
  public void setInfragenericEpithet(String infraGeneric) {
    if (setNothoIfHybrid(infraGeneric, NamePart.INFRAGENERIC)) {
      this.infragenericEpithet = infraGeneric.substring(1);
    } else {
      this.infragenericEpithet = infraGeneric;
    }
  }
  
  public String getSpecificEpithet() {
    return specificEpithet;
  }
  
  public void setSpecificEpithet(String species) {
    if (setNothoIfHybrid(species, NamePart.SPECIFIC)) {
      specificEpithet = species.substring(1);
    } else {
      specificEpithet = species;
    }
  }
  
  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }
  
  public void setInfraspecificEpithet(String infraSpecies) {
    if (setNothoIfHybrid(infraSpecies, NamePart.INFRASPECIFIC)) {
      this.infraspecificEpithet = infraSpecies.substring(1);
    } else {
      this.infraspecificEpithet = infraSpecies;
    }
  }
  
  public String getCultivarEpithet() {
    return cultivarEpithet;
  }
  
  public void setCultivarEpithet(String cultivarEpithet) {
    this.cultivarEpithet = cultivarEpithet;
  }

  @JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
  public boolean isCandidatus() {
    return candidatus;
  }
  
  public void setCandidatus(boolean candidatus) {
    this.candidatus = candidatus;
  }
  
  public NamePart getNotho() {
    return notho;
  }
  
  public void setNotho(NamePart notho) {
    this.notho = notho;
  }

  public NameType getType() {
    return type;
  }
  
  public void setType(NameType type) {
    this.type = type;
  }

  /**
   * @return true if any kind of authorship exists
   */
  @JsonIgnore
  public boolean hasAuthorship() {
    return hasCombinationAuthorship() || hasBasionymAuthorship();
  }

  /**
   * @return true if there is any parsed content
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public boolean isParsed() {
    return uninomial != null || genus != null || infragenericEpithet != null
        || specificEpithet != null || infraspecificEpithet != null || cultivarEpithet != null;
  }

  /**
   * Full name.O
   * @return same as canonicalNameComplete but formatted with basic html tags
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String labelHtml() {
    return getLabel(true);
  }

  @JsonIgnore
  public String getLabel() {
    return getLabel(false);
  }

  public String getLabel(boolean html) {
    return getLabelBuilder(html).toString();
  }

  StringBuilder getLabelBuilder(boolean html) {
    StringBuilder sb = new StringBuilder();
    String name = html ? scientificNameHtml() : scientificName;
    if (name != null) {
      sb.append(name);
    }
    if (authorship != null) {
      sb.append(" ");
      sb.append(authorship);
    }
    return sb;
  }

  @Override
  public void setCreatedBy(Integer createdBy) {
    // dont do anything, we do not store the creator in the database
  }

  @Override
  public void setModifiedBy(Integer modifiedBy) {
    // dont do anything, we do not store the modifier in the database
  }

  /**
   * Adds italics around the epithets but not rank markers or higher ranked names.
   */
  String scientificNameHtml(){
    return Name.scientificNameHtml(scientificName, rank, isParsed());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IndexName)) return false;
    if (!super.equals(o)) return false;
    IndexName indexName = (IndexName) o;
    return candidatus == indexName.candidatus &&
      Objects.equals(key, indexName.key) &&
      Objects.equals(canonicalId, indexName.canonicalId) &&
      scientificName.equals(indexName.scientificName) &&
      Objects.equals(authorship, indexName.authorship) &&
      rank == indexName.rank &&
      Objects.equals(uninomial, indexName.uninomial) &&
      Objects.equals(genus, indexName.genus) &&
      Objects.equals(infragenericEpithet, indexName.infragenericEpithet) &&
      Objects.equals(specificEpithet, indexName.specificEpithet) &&
      Objects.equals(infraspecificEpithet, indexName.infraspecificEpithet) &&
      Objects.equals(cultivarEpithet, indexName.cultivarEpithet) &&
      notho == indexName.notho &&
      Objects.equals(combinationAuthorship, indexName.combinationAuthorship) &&
      Objects.equals(basionymAuthorship, indexName.basionymAuthorship) &&
      Objects.equals(sanctioningAuthor, indexName.sanctioningAuthor) &&
      code == indexName.code &&
      type == indexName.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, canonicalId, scientificName, authorship, rank, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet, candidatus, notho, combinationAuthorship, basionymAuthorship, sanctioningAuthor, code, type);
  }

  @Override
  public String toString() {
    return key + " " + getLabel(false);
  }
  
}
