package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import life.catalogue.api.jackson.IsEmptyFilter;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.Origin;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.api.*;
import org.gbif.nameparser.util.NameFormatter;

import javax.annotation.Nonnull;
import java.net.URI;
import java.util.List;
import java.util.Objects;

import static org.gbif.nameparser.util.NameFormatter.HYBRID_MARKER;

/**
 *
 */
public class Name extends DatasetScopedEntity<String> implements VerbatimEntity, LinneanName {
  

  private Integer sectorKey;
  private Integer verbatimKey;
  
  /**
   * Groups all homotypic names by referring to a single representative name of that group.
   * This representative name is not necessarily the basionym, but often will be.
   * For basionyms this should link to the basionym name itself, this id=homotypicNameId
   */
  private String homotypicNameId;
  
  /**
   * Id from the names index grouping all distinct scientific names
   */
  //@JsonIgnore
  private String nameIndexId;
  
  /**
   * Match type that resulted in the nameIndexId.
   */
  //@JsonIgnore
  private MatchType nameIndexMatchType;
  
  /**
   * Entire canonical name string with a rank marker for infragenerics and infraspecfics, but
   * excluding the authorship. For uninomials, e.g. families or names at higher ranks, this is just
   * the uninomial.
   */
  @Nonnull
  private String scientificName;
  
  private String authorship;

  /**
   * Rank of the name from enumeration
   */
  private Rank rank;
  
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
  
  private String appendedPhrase;
  
  /**
   * A bacterial candidate name. Candidatus is a provisional status for incompletely described
   * procaryotes (e.g. that cannot be maintained in a Bacteriology Culture Collection) which was
   * published in the January 1995. The category Candidatus is not covered by the Rules of the
   * Bacteriological Code but is a taxonomic assignment.
   * <p>
   * The names included in the category Candidatus are usually written as follows: Candidatus (in
   * italics), the subsequent name(s) in roman type and the entire name in quotation marks. For
   * example, "Candidatus Phytoplasma", "Candidatus Phytoplasma allocasuarinae".
   * <p>
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
  private String publishedInId;
  
  /**
   * The page(s) or other detailed location within the publishedIn reference the name was described.
   */
  private String publishedInPage;
  
  /**
   * Year the name was published. Taken either from the authorship
   * or if not existing (e.g. botanical names) from the published in reference
   *
   * The value is readonly!
   */
  private Integer publishedInYear;

  @Nonnull
  private Origin origin;
  
  /**
   * The kind of name classified in broad categories based on their syntactical structure
   */
  private NameType type;
  
  private URI link;
  
  /**
   * Any informal note about the nomenclature of the name
   */
  private String remarks;
  
  public Name() {
  }
  
  /**
   * Creates a shallow copy of the provided Name instance.
   *
   * @param n
   */
  public Name(Name n) {
    this.setKey(n);
    this.sectorKey = n.sectorKey;
    this.homotypicNameId = n.homotypicNameId;
    this.nameIndexId = n.nameIndexId;
    this.nameIndexMatchType = n.nameIndexMatchType;
    this.scientificName = n.scientificName;
    this.rank = n.rank;
    this.uninomial = n.uninomial;
    this.genus = n.genus;
    this.infragenericEpithet = n.infragenericEpithet;
    this.specificEpithet = n.specificEpithet;
    this.infraspecificEpithet = n.infraspecificEpithet;
    this.cultivarEpithet = n.cultivarEpithet;
    this.appendedPhrase = n.appendedPhrase;
    this.candidatus = n.candidatus;
    this.notho = n.notho;
    this.combinationAuthorship = n.combinationAuthorship;
    this.basionymAuthorship = n.basionymAuthorship;
    this.sanctioningAuthor = n.sanctioningAuthor;
    this.code = n.code;
    this.nomStatus = n.nomStatus;
    this.publishedInId = n.publishedInId;
    this.publishedInPage = n.publishedInPage;
    this.publishedInYear = n.publishedInYear;
    this.origin = n.origin;
    this.type = n.type;
    this.link = n.link;
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
    pn.setStrain(n.getAppendedPhrase());
    pn.setCombinationAuthorship(n.getCombinationAuthorship());
    pn.setBasionymAuthorship(n.getBasionymAuthorship());
    pn.setSanctioningAuthor(n.getSanctioningAuthor());
    pn.setRank(n.getRank());
    pn.setCode(n.getCode());
    pn.setCandidatus(pn.isCandidatus());
    pn.setNotho(n.getNotho());
    pn.setNomenclaturalNote(n.getRemarks());
    pn.setType(n.getType());
    return pn;
  }
  
  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public String getNameIndexId() {
    return nameIndexId;
  }
  
  public void setNameIndexId(String nameIndexId) {
    this.nameIndexId = nameIndexId;
  }
  
  public MatchType getNameIndexMatchType() {
    return nameIndexMatchType;
  }
  
  public void setNameIndexMatchType(MatchType nameIndexMatchType) {
    this.nameIndexMatchType = nameIndexMatchType;
  }
  
  public String getScientificName() {
    return scientificName;
  }
  
  /**
   * @return a normalized version of the scientific name useful for matching. Only used on db level
   */
  @JsonIgnore
  public String getScientificNameNormalized() {
    return SciNameNormalizer.normalize(scientificName);
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
  public String getAuthorship() {
    return authorship;
  }

  /**
   * Normalized authorship - only internal and not meant for API use!
   */
  @JsonIgnore
  public List<String> getAuthorshipNormalized() {
    return AuthorshipNormalizer.INSTANCE.normalizeName(this);
  }

  /**
   * WARN: avoid setting the cached complete authorship for parsed names directly.
   * Use updateNameCache() instead!
   */
  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }
  
  /**
   * Updates the scientific name and authorship properties
   * based on the parsed properties for parsed names only.
   */
  public void updateNameCache() {
    if (isParsed()) {
      this.scientificName = canonicalNameWithoutAuthorship();
      this.authorship = authorshipComplete();
    }
  }
  
  public String getPublishedInId() {
    return publishedInId;
  }
  
  public void setPublishedInId(String publishedInId) {
    this.publishedInId = publishedInId;
  }
  
  public String getPublishedInPage() {
    return publishedInPage;
  }
  
  public void setPublishedInPage(String publishedInPage) {
    this.publishedInPage = publishedInPage;
  }
  
  public Integer getPublishedInYear() {
    return publishedInYear;
  }
  
  public void setPublishedInYear(Integer publishedInYear) {
    this.publishedInYear = publishedInYear;
  }
  
  public Origin getOrigin() {
    return origin;
  }
  
  public void setOrigin(Origin origin) {
    this.origin = origin;
  }
  
  public String getHomotypicNameId() {
    return homotypicNameId;
  }
  
  public void setHomotypicNameId(String homotypicNameId) {
    this.homotypicNameId = homotypicNameId;
  }
  
  public NomStatus getNomStatus() {
    return nomStatus;
  }
  
  public void setNomStatus(NomStatus nomStatus) {
    this.nomStatus = nomStatus;
  }
  
  public URI getLink() {
    return link;
  }
  
  public void setLink(URI link) {
    this.link = link;
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
  
  public String getAppendedPhrase() {
    return appendedPhrase;
  }
  
  public void setAppendedPhrase(String appendedPhrase) {
    this.appendedPhrase = appendedPhrase;
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
  
  public String getEpithet(NamePart part) {
    switch (part) {
      case GENERIC:
        return ObjectUtils.coalesce(getGenus(), getUninomial());
      case INFRAGENERIC:
        return getInfragenericEpithet();
      case SPECIFIC:
        return getSpecificEpithet();
      case INFRASPECIFIC:
        return getInfraspecificEpithet();
    }
    return null;
  }
  
  /**
   * @return true if any kind of authorship exists
   */
  @JsonIgnore
  public boolean hasAuthorship() {
    return hasCombinationAuthorship() || hasBasionymAuthorship();
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
   * epithet given.
   */
  @JsonIgnore
  public boolean isTrinomial() {
    return isBinomial() && infraspecificEpithet != null;
  }
  
  @JsonIgnore
  public boolean isIndetermined() {
    return isParsed() && (
        rank.isInfragenericStrictly() && infragenericEpithet == null && specificEpithet == null
        || rank.isSpeciesOrBelow() && specificEpithet == null
        || rank.isCultivarRank() && cultivarEpithet == null
        || rank.isInfraspecific() && !rank.isCultivarRank() && infraspecificEpithet == null
    );
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
   * @return true if the name status is potentially available
   */
  @JsonIgnore
  public boolean isAvailable() {
    return nomStatus == null || nomStatus.isAvailable();
  }
  
  /**
   * @return true if the name status is potentially legitimate
   */
  @JsonIgnore
  public boolean isLegitimate() {
    return nomStatus == null || nomStatus.isLegitimate();
  }
  
  /**
   * @return true if there is no parsed content or a scientific name. All canonical name methods
   * should return null in this case!
   */
  @JsonIgnore
  public boolean isEmpty() {
    return scientificName == null && !isParsed();
  }
  
  /**
   * Lists all non empty atomized name parts for parsed names.
   * Cultivar epithets, authorship and strains are excluded.
   *
   * @return all non null name parts
   */
  public List<String> nameParts() {
    List<String> parts = Lists.newArrayList(uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet);
    parts.removeIf(Objects::isNull);
    return parts;
  }
  
  /**
   * Excludes remarks, authorship & sensu
   * @See NameFormatter.canonicalNameWithoutAuthorship()
   */
  public String canonicalNameWithoutAuthorship() {
    return completeName(false, false, false);
  }
 
  /**
   * Includes all but remarks
   * @See NameFormatter.canonicalComplete()
   */
  public String canonicalNameWithAuthorship() {
    return completeName(false, true, false);
  }

  /**
   * Full name.
   */
  public String canonicalNameComplete() {
    return completeName(false, true, true);
  }

  /**
   * Full name.O
   * @return same as canonicalNameComplete but formatted with basic html tags
   */
  @JsonProperty(value = "formattedName", access = JsonProperty.Access.READ_ONLY)
  public String canonicalNameCompleteHtml() {
    return completeName(true, true, true);
  }
  
  private String completeName(boolean html, boolean authorship, boolean nomNote) {
    return isParsed() ?
        NameFormatter.buildName(toParsedName(this), true, true, authorship, true, true, true, false, true, true, nomNote, true, true, true, html)
        : scientificNameAuthorship();
  }
  
  /**
   * @return the scientificName plus authorship from cached fields, not parsed ones.
   */
  @JsonIgnore
  public String scientificNameAuthorship() {
    StringBuilder sb = new StringBuilder();
    sb.append(scientificName);
    if (authorship != null) {
      sb.append(" ");
      sb.append(authorship);
    }
    return sb.toString();
  }
  
  /**
   * @See NameFormatter.authorshipComplete()
   */
  public String authorshipComplete() {
    return NameFormatter.authorshipComplete(toParsedName(this));
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Name name = (Name) o;
    return candidatus == name.candidatus &&
        Objects.equals(sectorKey, name.sectorKey) &&
        Objects.equals(verbatimKey, name.verbatimKey) &&
        Objects.equals(homotypicNameId, name.homotypicNameId) &&
        Objects.equals(nameIndexId, name.nameIndexId) &&
        nameIndexMatchType == name.nameIndexMatchType &&
        Objects.equals(scientificName, name.scientificName) &&
        Objects.equals(authorship, name.authorship) &&
        rank == name.rank &&
        Objects.equals(uninomial, name.uninomial) &&
        Objects.equals(genus, name.genus) &&
        Objects.equals(infragenericEpithet, name.infragenericEpithet) &&
        Objects.equals(specificEpithet, name.specificEpithet) &&
        Objects.equals(infraspecificEpithet, name.infraspecificEpithet) &&
        Objects.equals(cultivarEpithet, name.cultivarEpithet) &&
        Objects.equals(appendedPhrase, name.appendedPhrase) &&
        notho == name.notho &&
        Objects.equals(combinationAuthorship, name.combinationAuthorship) &&
        Objects.equals(basionymAuthorship, name.basionymAuthorship) &&
        Objects.equals(sanctioningAuthor, name.sanctioningAuthor) &&
        code == name.code &&
        nomStatus == name.nomStatus &&
        Objects.equals(publishedInId, name.publishedInId) &&
        Objects.equals(publishedInPage, name.publishedInPage) &&
        Objects.equals(publishedInYear, name.publishedInYear) &&
        origin == name.origin &&
        type == name.type &&
        Objects.equals(link, name.link) &&
        Objects.equals(remarks, name.remarks);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, verbatimKey, homotypicNameId, nameIndexId, nameIndexMatchType, scientificName, authorship, rank, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet, appendedPhrase, candidatus, notho, combinationAuthorship, basionymAuthorship, sanctioningAuthor, code, nomStatus, publishedInId, publishedInPage, publishedInYear, origin, type, link, remarks);
  }
  
  @Override
  public String toString() {
    return getId() + " " + canonicalNameWithAuthorship();
  }
  
  public String toStringComplete() {
    StringBuilder sb = new StringBuilder();
  
    if (getId() != null) {
      sb.append(getId());
    }
    
    if (this.type != null) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append("[");
      sb.append(this.type);
      sb.append("]");
    }
    
    if (isParsed()) {
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
      
      if (this.appendedPhrase != null) {
        sb.append(" AP:").append(this.appendedPhrase);
      }
      
      if (this.combinationAuthorship != null) {
        sb.append(" A:").append(this.combinationAuthorship);
      }
      
      if (this.basionymAuthorship != null) {
        sb.append(" BA:").append(this.basionymAuthorship);
      }
      
    } else {
      sb.append(" SN:").append(this.scientificName);
      sb.append(" AUTH:").append(this.authorship);
  
    }
    
    if (this.publishedInId != null) {
      sb.append(" PUB:").append(this.publishedInId);
      if (this.publishedInPage != null) {
        sb.append(" ").append(this.publishedInPage);
      }
    }
    
    return sb.toString();
  }
  
}
