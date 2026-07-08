package life.catalogue.api.model;

import life.catalogue.common.tax.NameFormatter;
import life.catalogue.common.text.StringUtils;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

/**
 * A canonical name that belongs to the single-tier names index.
 * Contains just the canonical Name properties - the atomized name parts and the cached canonical
 * scientific name - but removes all dataset, verbatim, sector extras. It is also code agnostic.
 *
 * The names index is single-tier: every entry is a canonical name. It therefore carries no
 * authorship and its rank is always the standard {@link #CANONICAL_RANK} (UNRANKED); the
 * {@link FormattableName} rank/authorship accessors below are implemented as constants
 * accordingly. Because every entry is its own canonical, its canonical id always equals its own
 * key - there is no separate canonicalId field or column anymore.
 */
public class IndexName extends DataEntity<Integer> implements FormattableName {
  public static final Rank CANONICAL_RANK = Rank.UNRANKED;

  @JsonProperty("id")
  private Integer key;
  @Nonnull
  private String scientificName;
  private String uninomial;
  private String genus;
  private String infragenericEpithet; // we only use this for true infrageneric names, not bi-/trinomials!
  private String specificEpithet;
  private String infraspecificEpithet;
  private String cultivarEpithet;

  public IndexName() {
  }

  public IndexName(IndexName other) {
    super(other);
    this.key = other.key;
    this.scientificName = other.scientificName;
    this.uninomial = other.uninomial;
    this.genus = other.genus;
    this.infragenericEpithet = other.infragenericEpithet;
    this.specificEpithet = other.specificEpithet;
    this.infraspecificEpithet = other.infraspecificEpithet;
    this.cultivarEpithet = other.cultivarEpithet;
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
    this.uninomial = n.getUninomial();
    this.genus = n.getGenus();
    this.infragenericEpithet = n.getInfragenericEpithet();
    this.specificEpithet = n.getSpecificEpithet();
    this.infraspecificEpithet = n.getInfraspecificEpithet();
    this.cultivarEpithet = n.getCultivarEpithet();
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
   * Creates a new canonical index name from an existing name, keeping only the canonical name parts
   * and the canonical scientific name string. Rank and authorship are read from the given source
   * (which for a parsed {@link Name} natively carries a real rank) purely to decide the canonical
   * shape - they are never stored on the result, which is always a rankless, authorless canonical.
   */
  public static IndexName newCanonical(FormattableName n) {
    IndexName cn = new IndexName();
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
   * The names index is single-tier & canonical-only, so entries never carry authorship.
   * @return always null
   */
  @Override
  public String getAuthorship() {
    return null;
  }

  /**
   * No-op: canonical names index entries never carry authorship.
   */
  public void setAuthorship(String authorship) {
    // no-op - canonical entries have no authorship
  }

  /**
   * The names index is single-tier & canonical-only, so entries never carry authorship.
   * @return always null
   */
  @Override
  public Authorship getCombinationAuthorship() {
    return null;
  }

  public void setCombinationAuthorship(Authorship combinationAuthorship) {
    // no-op - canonical entries have no authorship
  }

  /**
   * The names index is single-tier & canonical-only, so entries never carry authorship.
   * @return always null
   */
  @Override
  public Authorship getBasionymAuthorship() {
    return null;
  }

  public void setBasionymAuthorship(Authorship basionymAuthorship) {
    // no-op - canonical entries have no authorship
  }

  /**
   * The names index is single-tier & canonical-only, so entries never carry authorship.
   * @return always null
   */
  @Override
  public String getSanctioningAuthor() {
    return null;
  }

  public void setSanctioningAuthor(String sanctioningAuthor) {
    // no-op - canonical entries have no authorship
  }

  /**
   * The names index is single-tier & canonical-only, so every entry has the standard canonical rank.
   * @return always {@link #CANONICAL_RANK}
   */
  @Override
  public Rank getRank() {
    return CANONICAL_RANK;
  }

  /**
   * No-op: canonical names index entries always have the standard {@link #CANONICAL_RANK}.
   */
  @Override
  public void setRank(Rank rank) {
    // no-op - canonical entries are always CANONICAL_RANK
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
  public Set<NamePart> getNotho() {
    return Set.of();
  }

  @Override
  public void setNotho(NamePart namePart) {
    // ignore
  }

  @Override
  public void addNotho(NamePart namePart) {
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
   * The names index is single-tier: every persisted entry is its own canonical name, so this is
   * always true once a key has been assigned.
   * @return true if this represents a canonical name in the index
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public boolean isCanonical() {
    return key != null;
  }

  /**
   * The names index is single-tier & canonical-only, so every entry qualifies as canonical:
   * it has the standard {@link #CANONICAL_RANK} and no authorship by construction.
   * @return true if this represents a canonical name in the index
   */
  @JsonIgnore
  public boolean qualifiesAsCanonical() {
    return getRank() == CANONICAL_RANK && !hasAuthorship();
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
    // canonical entries have no authorship, so the label is just the scientific name
    String name = html ? scientificNameHtml() : scientificName;
    if (name != null) {
      sb.append(name);
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
    return NameFormatter.scientificNameHtml(scientificName, getRank());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IndexName)) return false;
    if (!super.equals(o)) return false;
    IndexName indexName = (IndexName) o;
    return Objects.equals(key, indexName.key) &&
      scientificName.equals(indexName.scientificName) &&
      Objects.equals(uninomial, indexName.uninomial) &&
      Objects.equals(genus, indexName.genus) &&
      Objects.equals(infragenericEpithet, indexName.infragenericEpithet) &&
      Objects.equals(specificEpithet, indexName.specificEpithet) &&
      Objects.equals(infraspecificEpithet, indexName.infraspecificEpithet) &&
      Objects.equals(cultivarEpithet, indexName.cultivarEpithet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, scientificName, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet);
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
