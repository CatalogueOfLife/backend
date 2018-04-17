package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.col.api.jackson.IsEmptyFilter;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.Origin;
import org.gbif.nameparser.api.*;
import org.gbif.nameparser.util.NameFormatter;

import javax.annotation.Nonnull;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.gbif.nameparser.util.NameFormatter.HYBRID_MARKER;

/**
 *
 */
public class Name implements PrimaryEntity {

  /**
   * Internal surrogate key of the name as provided by postgres. This key is unique across all
   * datasets but not exposed in the API.
   */
  private Integer key;

  /**
   * Primary key of the name as given in the dataset dwc:scientificNameID. Only guaranteed to be
   * unique within a dataset and can follow any kind of schema.
   */
  private String id;

  /**
   * Key to dataset instance. Defines context of the name key.
   */
  private Integer datasetKey;

  /**
   * Groups all homotypic names by referring to a single representative name of that group.
   * This representative name is not necessarily the basionym, but often will be.
   * For basionyms this should link to the basionym name itself, this key=homotypicNameKey
   */
  private Integer homotypicNameKey;

  /**
   * Global name identifier from a nomenclator or the clearinghouse
   */
  private String scientificNameID;

  /**
   * Entire canonical name string with a rank marker for infragenerics and infraspecfics, but
   * excluding the authorship. For uninomials, e.g. families or names at higher ranks, this is just
   * the uninomial.
   */
  private String scientificName;

  /**
   * Rank of the name from enumeration
   */
  @Nonnull
  private Rank rank = Rank.UNRANKED;

  /**
   * Represents the monomial for genus, families or names at higher ranks which do not have further
   * epithets.
   */
  private String uninomial;

  /**
   * The genus part of a bi- or trinomial name. Not used for genus names which are represented by
   * the scientificName alone.
   */
  private String genus;

  /**
   * The infrageneric epithet. Used as the terminal epithet for names at infrageneric ranks and
   * optionally also for binomials
   */
  private String infragenericEpithet;

  private String specificEpithet;

  private String infraspecificEpithet;

  private String cultivarEpithet;

  private String strain;

  /**
   * A bacterial candidate name. Candidatus is a provisional status for incompletely described
   * procaryotes (e.g. that cannot be maintained in a Bacteriology Culture Collection) which was
   * published in the January 1995. The category Candidatus is not covered by the Rules of the
   * Bacteriological Code but is a taxonomic assignment.
   *
   * The names included in the category Candidatus are usually written as follows: Candidatus (in
   * italics), the subsequent name(s) in roman type and the entire name in quotation marks. For
   * example, "Candidatus Phytoplasma", "Candidatus Phytoplasma allocasuarinae".
   *
   * See http://www.bacterio.net/-candidatus.html and https://en.wikipedia.org/wiki/Candidatus
   */
  private boolean candidatus;

  /**
   * The part of the named hybrid which is considered a hybrid
   */
  private NamePart notho;

  /**
   * Authorship with years of the name, but excluding any basionym authorship. For binomials the
   * combination authors.
   */
  @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IsEmptyFilter.class)
  private Authorship combinationAuthorship = new Authorship();

  /**
   * Basionym authorship with years of the name
   */
  @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IsEmptyFilter.class)
  private Authorship basionymAuthorship = new Authorship();

  /**
   * The sanctioning author for sanctioned fungal names. Fr. or Pers.
   */
  private String sanctioningAuthor;

  private NomCode code;

  /**
   * Current nomenclatural status of the name taking into account all known nomenclatural acts.
   */
  private NomStatus nomStatus;

  /**
   * The reference the name was originally published in.
   */
  private Integer publishedInKey;

  /**
   * The page(s) or other detailed location within the publishedIn reference the name was described.
   */
  private String publishedInPage;

  private Origin origin;

  /**
   * The kind of name classified in broad catagories based on their syntactical structure
   */
  private NameType type;

  private URI sourceUrl;

  /**
   * true if the type specimen of the name is a fossil
   */
  private Boolean fossil;

  /**
   * Any informal note about the nomecnlatural status found in the name
   */
  private String remarks;

  /**
   * Issues related to this name
   */
  private Set<Issue> issues = EnumSet.noneOf(Issue.class);

  public Name() {}

  /**
   * Creates a shallow copy of the provided Name instance.
   * @param n
   */
  public Name(Name n) {
    this.key = n.key;
    this.id = n.id;
    this.datasetKey = n.datasetKey;
    this.homotypicNameKey = n.homotypicNameKey;
    this.scientificNameID = n.scientificNameID;
    this.scientificName = n.scientificName;
    this.rank = n.rank;
    this.uninomial = n.uninomial;
    this.genus = n.genus;
    this.infragenericEpithet = n.infragenericEpithet;
    this.specificEpithet = n.specificEpithet;
    this.infraspecificEpithet = n.infraspecificEpithet;
    this.cultivarEpithet = n.cultivarEpithet;
    this.strain = n.strain;
    this.candidatus = n.candidatus;
    this.notho = n.notho;
    this.combinationAuthorship = n.combinationAuthorship;
    this.basionymAuthorship = n.basionymAuthorship;
    this.sanctioningAuthor = n.sanctioningAuthor;
    this.code = n.code;
    this.nomStatus = n.nomStatus;
    this.publishedInKey = n.publishedInKey;
    this.publishedInPage = n.publishedInPage;
    this.origin = n.origin;
    this.type = n.type;
    this.sourceUrl = n.sourceUrl;
    this.fossil = n.fossil;
    this.issues = n.issues;
    this.remarks = n.remarks;
  }

  /**
   * @return a ParsedName instance representing this name
   */
  public static ParsedName toParsedName(Name n) {
    ParsedName pn = new ParsedName();
    pn.setUninomial(n.getUninomial());
    pn.setGenus(n.getGenus());
    pn.setInfragenericEpithet(n.getInfragenericEpithet());
    pn.setSpecificEpithet(n.getSpecificEpithet());
    pn.setInfraspecificEpithet(n.getInfraspecificEpithet());
    pn.setCultivarEpithet(n.getCultivarEpithet());
    pn.setStrain(n.getStrain());
    pn.setCombinationAuthorship(n.getCombinationAuthorship());
    pn.setBasionymAuthorship(n.getBasionymAuthorship());
    pn.setSanctioningAuthor(n.getSanctioningAuthor());
    pn.setRank(n.getRank());
    pn.setCode(n.getCode());
    pn.setCandidatus(pn.isCandidatus());
    pn.setNotho(n.getNotho());
    pn.setRemarks(n.getRemarks());
    pn.setType(n.getType());
    return pn;
  }

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

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer key) {
    this.datasetKey = key;
  }

  public String getScientificNameID() {
    return scientificNameID;
  }

  public void setScientificNameID(String scientificNameID) {
    this.scientificNameID = scientificNameID;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  /**
   * Updates the scientific name property based on the parsed properties
   */
  public void updateScientificName() {
    this.scientificName = canonicalNameWithoutAuthorship();
  }

  public Integer getPublishedInKey() {
    return publishedInKey;
  }

  public void setPublishedInKey(Integer publishedInKey) {
    this.publishedInKey = publishedInKey;
  }

  public String getPublishedInPage() {
    return publishedInPage;
  }

  public void setPublishedInPage(String publishedInPage) {
    this.publishedInPage = publishedInPage;
  }

  public Origin getOrigin() {
    return origin;
  }

  public void setOrigin(Origin origin) {
    this.origin = origin;
  }

  public Integer getHomotypicNameKey() {
    return homotypicNameKey;
  }

  public void setHomotypicNameKey(Integer homotypicNameKey) {
    this.homotypicNameKey = homotypicNameKey;
  }

  public Boolean getFossil() {
    return fossil;
  }

  public void setFossil(Boolean fossil) {
    this.fossil = fossil;
  }

  public NomStatus getNomStatus() {
    return nomStatus;
  }

  public void setNomStatus(NomStatus nomStatus) {
    this.nomStatus = nomStatus;
  }

  public URI getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(URI sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public Set<Issue> getIssues() {
    return issues;
  }

  public void setIssues(Set<Issue> issues) {
    this.issues = issues;
  }

  public void addIssue(Issue issue) {
    issues.add(issue);
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

  public String getStrain() {
    return strain;
  }

  public void setStrain(String strain) {
    this.strain = strain;
  }

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

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public void addRemark(String remark) {
    if (!StringUtils.isBlank(remark)) {
      this.remarks = remarks == null ? remark.trim() : remarks + "; " + remark.trim();
    }
  }

  public NameType getType() {
    return type;
  }

  public void setType(NameType type) {
    this.type = type;
  }

  /**
   * @return the terminal epithet. Infraspecific epithet if existing, the species epithet or null
   */
  @JsonIgnore
  public String getTerminalEpithet() {
    return infraspecificEpithet == null ? specificEpithet : infraspecificEpithet;
  }

  /**
   * @return true if any kind of authorship exists
   */
  @JsonIgnore
  public boolean hasAuthorship() {
    return combinationAuthorship.exists() || basionymAuthorship.exists();
  }

  @JsonIgnore
  public boolean isAutonym() {
    return specificEpithet != null && infraspecificEpithet != null
        && specificEpithet.equals(infraspecificEpithet);
  }

  /**
   * @return true if the name is a bi- or trinomial with at least a genus and species epithet given.
   */
  @JsonIgnore
  public boolean isBinomial() {
    return genus != null && specificEpithet != null;
  }

  /**
   * @return true if the name is a trinomial with at least a genus, species and infraspecific
   *         epithet given.
   */
  @JsonIgnore
  public boolean isTrinomial() {
    return isBinomial() && infraspecificEpithet != null;
  }

  @JsonIgnore
  public boolean isIndetermined() {
    return rank.isInfragenericStrictly() && infragenericEpithet == null && specificEpithet == null
        || rank.isSpeciesOrBelow() && specificEpithet == null
        || rank.isCultivarRank() && cultivarEpithet == null
        || rank.isInfraspecific() && !rank.isCultivarRank() && infraspecificEpithet == null;
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
   * @return true if there is no parsed content or a scientific name. All canonical name methods
   *         should return null in this case!
   */
  @JsonIgnore
  public boolean isEmpty() {
    return scientificName == null && !isParsed();
  }

  /**
   * Lists all non empty atomized name parts for parsed names.
   * Cultivar epithets, authorship and strains are excluded.
   * @return all non null name parts
   */
  public List<String> nameParts() {
    List<String> parts = Lists.newArrayList(uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet);
    parts.removeIf(Objects::isNull);
    return parts;
  }

  /**
   * @See NameFormatter.canonical()
   */
  public String canonicalName() {
    return isParsed() ? NameFormatter.canonical(toParsedName(this)) : getScientificName();
  }

  /**
   * @See NameFormatter.canonicalNameWithoutAuthorship()
   */
  public String canonicalNameWithoutAuthorship() {
    return isParsed() ? NameFormatter.canonicalWithoutAuthorship(toParsedName(this))
        : getScientificName();
  }

  /**
   * @See NameFormatter.canonicalMinimal()
   */
  public String canonicalNameMinimal() {
    return isParsed() ? NameFormatter.canonicalMinimal(toParsedName(this)) : getScientificName();
  }

  /**
   * @See NameFormatter.canonicalComplete()
   */
  public String canonicalNameComplete() {
    return isParsed() ? NameFormatter.canonicalComplete(toParsedName(this)) : getScientificName();
  }

  /**
   * @See NameFormatter.authorshipComplete()
   */
  @JsonProperty(value = "authorship", access = JsonProperty.Access.READ_ONLY)
  public String authorshipComplete() {
    return NameFormatter.authorshipComplete(toParsedName(this));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Name name = (Name) o;
    return candidatus == name.candidatus &&
        Objects.equals(key, name.key) &&
        Objects.equals(id, name.id) &&
        Objects.equals(datasetKey, name.datasetKey) &&
        Objects.equals(homotypicNameKey, name.homotypicNameKey) &&
        Objects.equals(scientificNameID, name.scientificNameID) &&
        Objects.equals(scientificName, name.scientificName) &&
        rank == name.rank &&
        Objects.equals(uninomial, name.uninomial) &&
        Objects.equals(genus, name.genus) &&
        Objects.equals(infragenericEpithet, name.infragenericEpithet) &&
        Objects.equals(specificEpithet, name.specificEpithet) &&
        Objects.equals(infraspecificEpithet, name.infraspecificEpithet) &&
        Objects.equals(cultivarEpithet, name.cultivarEpithet) &&
        Objects.equals(strain, name.strain) &&
        notho == name.notho &&
        Objects.equals(combinationAuthorship, name.combinationAuthorship) &&
        Objects.equals(basionymAuthorship, name.basionymAuthorship) &&
        Objects.equals(sanctioningAuthor, name.sanctioningAuthor) &&
        code == name.code &&
        nomStatus == name.nomStatus &&
        Objects.equals(publishedInKey, name.publishedInKey) &&
        Objects.equals(publishedInPage, name.publishedInPage) &&
        origin == name.origin &&
        type == name.type &&
        Objects.equals(sourceUrl, name.sourceUrl) &&
        Objects.equals(fossil, name.fossil) &&
        Objects.equals(remarks, name.remarks) &&
        Objects.equals(issues, name.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, datasetKey, homotypicNameKey, scientificNameID, scientificName, rank, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet, strain, candidatus, notho, combinationAuthorship, basionymAuthorship, sanctioningAuthor, code, nomStatus, publishedInKey, publishedInPage, origin, type, sourceUrl, fossil, remarks, issues);
  }

  @Override
  public String toString() {
    return key + "[" + id + "] " + canonicalNameComplete();
  }

  public String toStringComplete() {
    StringBuilder sb = new StringBuilder();

    sb.append(key).append("/").append(id);

    if (this.type != null) {
      sb.append("[");
      sb.append(this.type);
      sb.append("] ");
    }

    if (this.uninomial != null) {
      sb.append(" U:").append(this.uninomial);
    }

    if (this.genus != null) {
      sb.append(" G:").append(this.genus);
    }

    if (this.infragenericEpithet != null) {
      sb.append(" IG:").append(this.infragenericEpithet);
    }

    if (this.specificEpithet != null) {
      sb.append(" S:").append(this.specificEpithet);
    }

    sb.append(" R:").append(this.rank);

    if (this.infraspecificEpithet != null) {
      sb.append(" IS:").append(this.infraspecificEpithet);
    }

    if (this.cultivarEpithet != null) {
      sb.append(" CV:").append(this.cultivarEpithet);
    }

    if (this.strain != null) {
      sb.append(" STR:").append(this.strain);
    }

    if (this.combinationAuthorship != null) {
      sb.append(" A:").append(this.combinationAuthorship);
    }

    if (this.basionymAuthorship != null) {
      sb.append(" BA:").append(this.basionymAuthorship);
    }

    if (this.publishedInKey != null) {
      sb.append(" PUB:").append(this.publishedInKey);
      if (this.publishedInPage != null) {
        sb.append(" ").append(this.publishedInPage);
      }
    }

    return sb.toString();
  }

}
