package life.catalogue.api.model;

import life.catalogue.api.jackson.IsEmptyFilter;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.common.text.StringUtils;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

/**
 * A parsed or unparsed name that belongs to the names index.
 * Contains all main Name properties but removes all dataset, verbatim, sector extras.
 * It is also code agnostic.
 *
 * An index name is considered a canonical name when it does not have any authorship
 * and the UNRANKED rank.
 * All other, parsed names point also to their canonical authorless version.
 */
public class IndexName extends DataEntity<Integer> implements FormattableName {
  public static final Rank CANONICAL_RANK = Rank.UNRANKED;

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
  private String infragenericEpithet; // we only use this for true infrageneric names, not bi-/trinomials!
  private String specificEpithet;
  private String infraspecificEpithet;
  private String cultivarEpithet;
  @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IsEmptyFilter.class)
  private Authorship combinationAuthorship = new Authorship();
  @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IsEmptyFilter.class)
  private Authorship basionymAuthorship = new Authorship();
  private String sanctioningAuthor;

  public IndexName() {
  }

  public IndexName(IndexName other) {
    super(other);
    this.key = other.key;
    this.canonicalId = other.canonicalId;
    this.scientificName = other.scientificName;
    this.authorship = other.authorship;
    this.rank = other.rank;
    this.uninomial = other.uninomial;
    this.genus = other.genus;
    this.infragenericEpithet = other.infragenericEpithet;
    this.specificEpithet = other.specificEpithet;
    this.infraspecificEpithet = other.infraspecificEpithet;
    this.cultivarEpithet = other.cultivarEpithet;
    this.combinationAuthorship = other.combinationAuthorship;
    this.basionymAuthorship = other.basionymAuthorship;
    this.sanctioningAuthor = other.sanctioningAuthor;
  }

  public IndexName(Name n) {
    boolean removeInfrageneric = n.getInfragenericEpithet() != null && (n.getRank() == null || !n.getRank().isInfragenericStrictly());
    String infragen = null;
    if (removeInfrageneric) {
      // only keep for strict infragenerics
      infragen = n.getInfragenericEpithet();
      n.setInfragenericEpithet(null);
      n.rebuildScientificName();
    }
    this.scientificName = n.getScientificName();
    this.authorship = n.getAuthorship();
    setRank(n.getRank());
    this.uninomial = n.getUninomial();
    this.genus = n.getGenus();
    this.infragenericEpithet = n.getInfragenericEpithet();
    this.specificEpithet = n.getSpecificEpithet();
    this.infraspecificEpithet = n.getInfraspecificEpithet();
    this.cultivarEpithet = n.getCultivarEpithet();
    this.combinationAuthorship = n.getCombinationAuthorship();
    this.basionymAuthorship = n.getBasionymAuthorship();
    this.sanctioningAuthor = n.getSanctioningAuthor();
    this.setCreated(n.getCreated());
    this.setModified(n.getModified());
    // revert name instance
    if (removeInfrageneric) {
      n.setInfragenericEpithet(infragen);
      n.rebuildScientificName();
    }
  }

  public IndexName(Name n, int key) {
    this(n);
    setKey(key);
  }

  /**
   * Creates a new canonical index name from an existing name with a standard rank and no authorship.
   */
  public static IndexName newCanonical(IndexName n) {
    IndexName cn = new IndexName();
    cn.setRank(CANONICAL_RANK);
    // we keep a canonical infrageneric name in uninomial and ignore its genus placement!
    if (n.getInfragenericEpithet() != null && n.isInfrageneric()) {
      cn.setUninomial(n.getInfragenericEpithet());
    } else {
      cn.setUninomial(n.getUninomial());
      cn.setGenus(n.getGenus());
      cn.setSpecificEpithet(n.getSpecificEpithet());
      cn.setInfraspecificEpithet(n.getInfraspecificEpithet());
      cn.setCultivarEpithet(n.getCultivarEpithet());
    }
    if (n.isParsed()) {
      cn.setScientificName(NameFormatter.canonicalName(n));
    } else {
      cn.setScientificName(n.getScientificName());
    }
    cn.setCreated(n.getCreated());
    cn.setCreatedBy(n.getCreatedBy());
    cn.setModified(n.getModified());
    cn.setModifiedBy(n.getModifiedBy());
    return cn;
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
    this.scientificName = Preconditions.checkNotNull(scientificName);
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
  
  public Authorship getCombinationAuthorship() {
    return combinationAuthorship;
  }
  
  public void setCombinationAuthorship(Authorship combinationAuthorship) {
    this.combinationAuthorship = combinationAuthorship;
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

  @Override
  public Rank getRank() {
    return rank;
  }

  @Override
  public void setRank(Rank rank) {
    this.rank = Preconditions.checkNotNull(rank);
  }

  @Override
  public NomCode getCode() {
    return null;
  }

  @Override
  public void setCode(NomCode code) {
  }
  
  public String getUninomial() {
    return uninomial;
  }
  
  public void setUninomial(String uni) {
    this.uninomial = StringUtils.removeHybrid(uni);
  }
  
  public String getGenus() {
    return genus;
  }
  
  public void setGenus(String genus) {
    this.genus = StringUtils.removeHybrid(genus);
  }
  
  public String getInfragenericEpithet() {
    return infragenericEpithet;
  }

  public void setInfragenericEpithet(String infraGeneric) {
    this.infragenericEpithet = StringUtils.removeHybrid(infraGeneric);
  }
  
  public String getSpecificEpithet() {
    return specificEpithet;
  }
  
  public void setSpecificEpithet(String species) {
    this.specificEpithet = StringUtils.removeHybrid(species);
  }
  
  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }
  
  public void setInfraspecificEpithet(String infraSpecies) {
    this.infraspecificEpithet = StringUtils.removeHybrid(infraSpecies);
  }

  @Override
  public NamePart getNotho() {
    return null;
  }

  @Override
  public void setNotho(NamePart namePart) {
    // ignore
  }

  public String getCultivarEpithet() {
    return cultivarEpithet;
  }

  @Override
  public boolean isCandidatus() {
    return false;
  }

  @Override
  public String getNomenclaturalNote() {
    return null;
  }

  @Override
  public String getUnparsed() {
    return null;
  }

  public void setCultivarEpithet(String cultivarEpithet) {
    this.cultivarEpithet = cultivarEpithet;
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
   * Override the default method to rely on the actual ids in the index to be precise.
   * @return true if this represents a canonical name in the index
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public boolean isCanonical() {
    return key != null && key.equals(canonicalId);
  }

  /**
   * Checks the names properties to see if it qualifies for a canonical name,
   * i.e. a name that has one of the few standard ranks and is
   *  a) unparsed or b) parsed, but without any authorship.
   * @return true if this represents a canonical name in the index
   */
  @JsonIgnore
  public boolean qualifiesAsCanonical() {
    return rank == CANONICAL_RANK && !hasAuthorship();
  }

  /**
   * Full name.O
   * @return same as canonicalNameComplete but formatted with basic html tags
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String labelHtml() {
    return getLabel(true);
  }

  @Override
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
    return NameFormatter.scientificNameHtml(scientificName, rank);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IndexName)) return false;
    if (!super.equals(o)) return false;
    IndexName indexName = (IndexName) o;
    return Objects.equals(key, indexName.key) &&
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
      Objects.equals(combinationAuthorship, indexName.combinationAuthorship) &&
      Objects.equals(basionymAuthorship, indexName.basionymAuthorship) &&
      Objects.equals(sanctioningAuthor, indexName.sanctioningAuthor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, canonicalId, scientificName, authorship, rank, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet, combinationAuthorship, basionymAuthorship, sanctioningAuthor);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(key);
    sb.append(" ");

    if (isCanonical()) {
      sb.append(getLabel());
      sb.append(" [CANONICAL]");
    } else {
      sb.append(getLabelWithRank());
    }
    return sb.toString();
  }
  
}
