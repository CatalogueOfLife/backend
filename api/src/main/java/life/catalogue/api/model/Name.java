package life.catalogue.api.model;

import life.catalogue.api.jackson.IsEmptyFilter;
import life.catalogue.api.vocab.Gender;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.Origin;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.NameFormatter;

import org.gbif.nameparser.api.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import static life.catalogue.common.tax.NameFormatter.HYBRID_MARKER;

/**
 * A parsed or unparsed name with strictly nomenclatural properties.
 * Any taxonomic information is kept with a NameUsage, toably a Taxon.
 *
 * Rank and the scientificName are the only guaranteed properties to exist for any name.
 * Only for parsed Names further atomised properties do exist, see {@link #isParsed()}.
 * For parsed names the scientificName is reconstructed based on the parsed information to make sure they match up.
 *
 * The {@link #getAuthorship()} on the other hand contains the original value and is unaltered for parsed names.
 * It is only reconstructed for record that did not contain the authorship separately from the bare scientific name.
 */
public class Name extends DatasetScopedEntity<String> implements VerbatimEntity, SectorEntity, FormattableName, Remarkable {

  private Integer sectorKey;
  private Integer verbatimKey;

  private Integer namesIndexId;
  private MatchType namesIndexType; // mybatis sets this to none if no match exists. We only ever have null in manually created instances

  private List<Identifier> identifier;

  /**
   * Entire canonical name string with a rank marker for infragenerics and infraspecfics, but
   * excluding the authorship.
   * For uninomials, e.g. families or names at higher ranks, this is just the uninomial.
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
   * epithets. Not to be used for subgeneric names, use infragenericEpithet instead.
   */
  private String uninomial;
  
  /**
   * The genus part of a bi- or trinomial name. Not used for genus names which are represented by
   * the uninomial alone. The genus can also be used to classify a subgeneric name.
   */
  private String genus;
  
  /**
   * The infrageneric epithet. Used as the terminal epithet for names at infrageneric ranks and
   * optionally also for binomials.
   * That means a subgenus should NOT be represented as a uninomial, but with infragenericEpithet
   */
  private String infragenericEpithet;
  
  private String specificEpithet;
  
  private String infraspecificEpithet;
  
  private String cultivarEpithet;

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
   * If true indicates that the name is given it the original spelling when an emendation exists.
   * This is usually indicated by placing [sic] after the name.
   *
   * If false instead it indicates that the name is a corrected spelling, usually indicated by placing corrig. after the name.
   *
   * If null it is unknown or the original spelling was never revised.
   */
  private Boolean originalSpelling;

  /**
   * Flag that indicates for bi/trinomials whether the (infra)species epithet must follow and agree with the gender of the genus.
   */
  private Boolean genderAgreement;

  /**
   * Gender of the name, i.e. the genus in case of bi/trinomials.
   */
  private Gender gender;

  /**
   * The reference the name was originally published in.
   */
  private String publishedInId;
  
  /**
   * The page(s) or other detailed location within the publishedIn reference the name was described.
   */
  private String publishedInPage;

  /**
   * The page(s) or other detailed location within the publishedIn reference the name was described.
   */
  private String publishedInPageLink;

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
   * Exact nomenclatural notes found in the status, authorship or publishedIn
   */
  private String nomenclaturalNote;

  private String unparsed;

  /**
   * Any informal note about the nomenclature of the name
   */
  private String remarks;
  
  public Name() {
  }

  public Name(SimpleName sn) {
    setScientificName(sn.getName());
    setAuthorship(sn.getAuthorship());
    setRank(sn.getRank());
    setCode(sn.getCode());
  }

  /**
   * Creates a shallow copy of the provided Name instance.
   *
   * @param n
   */
  public Name(Name n) {
    this.setKey(n);
    this.sectorKey = n.sectorKey;
    this.namesIndexId = n.namesIndexId;
    this.namesIndexType = n.namesIndexType;
    this.identifier = n.identifier;
    this.scientificName = n.scientificName;
    this.authorship = n.authorship;
    this.rank = n.rank;
    this.uninomial = n.uninomial;
    this.genus = n.genus;
    this.infragenericEpithet = n.infragenericEpithet;
    this.specificEpithet = n.specificEpithet;
    this.infraspecificEpithet = n.infraspecificEpithet;
    this.cultivarEpithet = n.cultivarEpithet;
    this.candidatus = n.candidatus;
    this.notho = n.notho;
    this.combinationAuthorship = n.combinationAuthorship;
    this.basionymAuthorship = n.basionymAuthorship;
    this.sanctioningAuthor = n.sanctioningAuthor;
    this.code = n.code;
    this.nomStatus = n.nomStatus;
    this.originalSpelling = n.originalSpelling;
    this.genderAgreement = n.genderAgreement;
    this.gender = n.gender;
    this.publishedInId = n.publishedInId;
    this.publishedInPage = n.publishedInPage;
    this.publishedInYear = n.publishedInYear;
    this.publishedInPageLink = n.publishedInPageLink;
    this.origin = n.origin;
    this.type = n.type;
    this.unparsed = n.unparsed;
    this.nomenclaturalNote = n.nomenclaturalNote;
    this.link = n.link;
    this.remarks = n.remarks;
    this.setCreatedBy(n.getCreatedBy());
    this.setCreated(n.getCreated());
    this.setModifiedBy(n.getModifiedBy());
    this.setModified(n.getModified());
  }

  private Name(Builder builder) {
    setCreated(builder.created);
    setCreatedBy(builder.createdBy);
    setModified(builder.modified);
    setModifiedBy(builder.modifiedBy);
    setDatasetKey(builder.datasetKey);
    setId(builder.id);
    setSectorKey(builder.sectorKey);
    setVerbatimKey(builder.verbatimKey);
    setNamesIndexId(builder.namesIndexId);
    setNamesIndexType(builder.namesIndexType);
    setScientificName(builder.scientificName);
    setAuthorship(builder.authorship);
    setRank(builder.rank);
    setUninomial(builder.uninomial);
    setGenus(builder.genus);
    setInfragenericEpithet(builder.infragenericEpithet);
    setSpecificEpithet(builder.specificEpithet);
    setInfraspecificEpithet(builder.infraspecificEpithet);
    setCultivarEpithet(builder.cultivarEpithet);
    setCandidatus(builder.candidatus);
    setNotho(builder.notho);
    setCombinationAuthorship(builder.combinationAuthorship);
    setBasionymAuthorship(builder.basionymAuthorship);
    setSanctioningAuthor(builder.sanctioningAuthor);
    setCode(builder.code);
    setNomStatus(builder.nomStatus);
    setOriginalSpelling(builder.originalSpelling);
    setGenderAgreement(builder.genderAgreement);
    setGender(builder.gender);
    setPublishedInId(builder.publishedInId);
    setPublishedInPage(builder.publishedInPage);
    publishedInPageLink = builder.publishedInPageLink;
    setPublishedInYear(builder.publishedInYear);
    setOrigin(builder.origin);
    setType(builder.type);
    setIdentifier(builder.identifier);
    setLink(builder.link);
    setNomenclaturalNote(builder.nomenclaturalNote);
    setUnparsed(builder.unparsed);
    setRemarks(builder.remarks);
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
    pn.setCombinationAuthorship(n.getCombinationAuthorship());
    pn.setBasionymAuthorship(n.getBasionymAuthorship());
    pn.setSanctioningAuthor(n.getSanctioningAuthor());
    pn.setRank(n.getRank());
    pn.setCode(n.getCode());
    pn.setCandidatus(pn.isCandidatus());
    pn.setNotho(n.getNotho());
    pn.setOriginalSpelling(n.isOriginalSpelling());
    pn.setNomenclaturalNote(n.getRemarks());
    pn.setType(n.getType());
    return pn;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(Name copy) {
    Builder builder = new Builder();
    builder.created = copy.getCreated();
    builder.createdBy = copy.getCreatedBy();
    builder.modified = copy.getModified();
    builder.modifiedBy = copy.getModifiedBy();
    builder.datasetKey = copy.getDatasetKey();
    builder.id = copy.getId();
    builder.sectorKey = copy.getSectorKey();
    builder.verbatimKey = copy.getVerbatimKey();
    builder.namesIndexId = copy.getNamesIndexId();
    builder.namesIndexType = copy.getNamesIndexType();
    builder.scientificName = copy.getScientificName();
    builder.authorship = copy.getAuthorship();
    builder.rank = copy.getRank();
    builder.uninomial = copy.getUninomial();
    builder.genus = copy.getGenus();
    builder.infragenericEpithet = copy.getInfragenericEpithet();
    builder.specificEpithet = copy.getSpecificEpithet();
    builder.infraspecificEpithet = copy.getInfraspecificEpithet();
    builder.cultivarEpithet = copy.getCultivarEpithet();
    builder.candidatus = copy.isCandidatus();
    builder.notho = copy.getNotho();
    builder.combinationAuthorship = copy.getCombinationAuthorship();
    builder.basionymAuthorship = copy.getBasionymAuthorship();
    builder.sanctioningAuthor = copy.getSanctioningAuthor();
    builder.code = copy.getCode();
    builder.nomStatus = copy.getNomStatus();
    builder.originalSpelling = copy.isOriginalSpelling();
    builder.genderAgreement = copy.genderAgreement;
    builder.gender = copy.gender;
    builder.publishedInId = copy.getPublishedInId();
    builder.publishedInPage = copy.getPublishedInPage();
    builder.publishedInPageLink = copy.getPublishedInPageLink();
    builder.publishedInYear = copy.getPublishedInYear();
    builder.origin = copy.getOrigin();
    builder.type = copy.getType();
    builder.identifier = copy.getIdentifier();
    builder.link = copy.getLink();
    builder.nomenclaturalNote = copy.getNomenclaturalNote();
    builder.unparsed = copy.getUnparsed();
    builder.remarks = copy.getRemarks();
    return builder;
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

  public Integer getNamesIndexId() {
    return namesIndexId;
  }

  public void setNamesIndexId(Integer namesIndexId) {
    this.namesIndexId = namesIndexId;
  }

  public MatchType getNamesIndexType() {
    return namesIndexType;
  }

  public void setNamesIndexType(MatchType namesIndexType) {
    this.namesIndexType = namesIndexType;
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
   * Updates the scientific name based on the parsed properties for parsed names only.
   */
  public void rebuildScientificName() {
    if (isParsed()) {
      this.scientificName = NameFormatter.scientificName(this);
    }
  }

  /**
   * Updates the authorship based on the parsed properties for parsed names only.
   * Use this with care as we want to keep the original authorship.
   */
  public void rebuildAuthorship() {
    if (isParsed()) {
      this.authorship = NameFormatter.authorship(this);
    }
  }

  public List<Identifier> getIdentifier() {
    return identifier;
  }

  public void setIdentifier(List<Identifier> identifier) {
    this.identifier = identifier;
  }

  public void addIdentifier(String identifier) {
    if (!StringUtils.isBlank(identifier)) {
      addIdentifier(Identifier.parse(identifier));
    }
  }

  public void addIdentifier(Identifier id) {
    if (this.identifier == null) {
      this.identifier = new ArrayList<>();
    }
    this.identifier.add(id);
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

  public String getPublishedInPageLink() {
    return publishedInPageLink;
  }

  public void setPublishedInPageLink(String publishedInPageLink) {
    this.publishedInPageLink = publishedInPageLink;
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

  public NomStatus getNomStatus() {
    return nomStatus;
  }
  
  public void setNomStatus(NomStatus nomStatus) {
    this.nomStatus = nomStatus;
  }

  @Override
  public Boolean isOriginalSpelling() {
    return originalSpelling;
  }

  public void setOriginalSpelling(Boolean originalSpelling) {
    this.originalSpelling = originalSpelling;
  }

  public Boolean hasGenderAgreement() {
    return genderAgreement;
  }

  public void setGenderAgreement(Boolean genderAgreement) {
    this.genderAgreement = genderAgreement;
  }

  public Gender getGender() {
    return gender;
  }

  public void setGender(Gender gender) {
    this.gender = gender;
  }

  public URI getLink() {
    return link;
  }
  
  public void setLink(URI link) {
    this.link = link;
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
  
  @Override
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
    this.rank = rank == null ? Rank.UNRANKED : rank;
  }

  public void setRankAllowNull(Rank rank) {
    this.rank = rank;
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
      specificEpithet = species.substring(1).trim();
    } else {
      specificEpithet = species;
    }
  }
  
  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }
  
  public void setInfraspecificEpithet(String infraSpecies) {
    if (setNothoIfHybrid(infraSpecies, NamePart.INFRASPECIFIC)) {
      this.infraspecificEpithet = infraSpecies.substring(1).trim();
    } else {
      this.infraspecificEpithet = infraSpecies;
    }
  }
  
  @Override
  public String getCultivarEpithet() {
    return cultivarEpithet;
  }
  
  public void setCultivarEpithet(String cultivarEpithet) {
    this.cultivarEpithet = cultivarEpithet;
  }

  @Override
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

  @Override
  public String getRemarks() {
    return remarks;
  }

  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public NameType getType() {
    return type;
  }
  
  public void setType(NameType type) {
    this.type = type;
  }

  @Override
  public String getNomenclaturalNote() {
    return nomenclaturalNote;
  }

  public void setNomenclaturalNote(String nomenclaturalNote) {
    this.nomenclaturalNote = nomenclaturalNote;
  }

  @Override
  public String getUnparsed() {
    return unparsed;
  }

  public void setUnparsed(String unparsed) {
    this.unparsed = unparsed;
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

  @Override
  public String getLabel() {
    return getLabel(false);
  }

  public String getLabelHtml() {
    return getLabel(true);
  }

  private String getLabel(boolean html) {
    return appendNameLabel(new StringBuilder(), null, html).toString();
  }

  /**
   * @param sb build to append to
   * @param preAuthorship optional prefix to be placed just before the authorship
   */
  StringBuilder appendNameLabel(StringBuilder sb, @Nullable String preAuthorship, boolean html) {
    String name = html ? NameFormatter.scientificNameHtml(scientificName, rank) : scientificName;
    if (name != null) {
      sb.append(name);
    }
    if (originalSpelling != null) {
      if (Boolean.TRUE.equals(originalSpelling)) {
        sb.append(" [sic]");
      } else if (Boolean.FALSE.equals(originalSpelling)) {
        sb.append(" corrig.");
      }
    }
    if (preAuthorship != null) {
      sb.append(" ");
      sb.append(preAuthorship);
    }
    if (authorship != null) {
      sb.append(" ");
      sb.append(authorship);
    }
    return sb;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Name)) return false;
    if (!super.equals(o)) return false;
    Name name = (Name) o;
    return candidatus == name.candidatus
           && Objects.equals(sectorKey, name.sectorKey)
           && Objects.equals(verbatimKey, name.verbatimKey)
           && Objects.equals(namesIndexId, name.namesIndexId)
           && namesIndexType == name.namesIndexType
           && Objects.equals(identifier, name.identifier)
           && Objects.equals(scientificName, name.scientificName)
           && Objects.equals(authorship, name.authorship)
           && rank == name.rank
           && Objects.equals(uninomial, name.uninomial)
           && Objects.equals(genus, name.genus)
           && Objects.equals(infragenericEpithet, name.infragenericEpithet)
           && Objects.equals(specificEpithet, name.specificEpithet)
           && Objects.equals(infraspecificEpithet, name.infraspecificEpithet)
           && Objects.equals(cultivarEpithet, name.cultivarEpithet)
           && notho == name.notho
           && Objects.equals(combinationAuthorship, name.combinationAuthorship)
           && Objects.equals(basionymAuthorship, name.basionymAuthorship)
           && Objects.equals(sanctioningAuthor, name.sanctioningAuthor)
           && code == name.code
           && nomStatus == name.nomStatus
           && Objects.equals(originalSpelling, name.originalSpelling)
           && Objects.equals(genderAgreement, name.genderAgreement)
           && gender == name.gender
           && Objects.equals(publishedInId, name.publishedInId)
           && Objects.equals(publishedInPage, name.publishedInPage)
           && Objects.equals(publishedInPageLink, name.publishedInPageLink)
           && Objects.equals(publishedInYear, name.publishedInYear)
           && origin == name.origin
           && type == name.type
           && Objects.equals(link, name.link)
           && Objects.equals(nomenclaturalNote, name.nomenclaturalNote)
           && Objects.equals(unparsed, name.unparsed)
           && Objects.equals(remarks, name.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, verbatimKey, namesIndexId, namesIndexType, identifier, scientificName, authorship, rank, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet, candidatus, notho, combinationAuthorship, basionymAuthorship, sanctioningAuthor, code, nomStatus, originalSpelling, genderAgreement, gender, publishedInId, publishedInPage, publishedInPageLink, publishedInYear, origin, type, link, nomenclaturalNote, unparsed, remarks);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getLabel(false));
    if (rank != null) {
      sb.append(" [");
      sb.append(rank);
      sb.append("]");
    }
    if (getId() != null) {
      sb.append(" {");
      sb.append(getId());
      sb.append("}");
    }
    return sb.toString();
  }
  
  public String toStringComplete() {
    StringBuilder sb = new StringBuilder();
  
    if (getId() != null) {
      sb.append(getId());
    }
    
    if (type != null) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append("[");
      if (isParsed()) {
        sb.append("Parsed ");
      }
      sb.append(type);
      sb.append("]");
    }
    if (sb.length() > 0) {
      sb.append(" ");
    }
    sb.append(getLabel(false));
    if (rank != null) {
      sb.append(" [");
      sb.append(rank);
      sb.append("]");
    }
    return sb.toString();
  }

  public static final class Builder {
    private LocalDateTime created;
    private Integer createdBy;
    private LocalDateTime modified;
    private Integer modifiedBy;
    private Integer datasetKey;
    private String id;
    private Integer sectorKey;
    private Integer verbatimKey;
    private Integer namesIndexId;
    private MatchType namesIndexType;
    private String scientificName;
    private String authorship;
    private Rank rank;
    private String uninomial;
    private String genus;
    private String infragenericEpithet;
    private String specificEpithet;
    private String infraspecificEpithet;
    private String cultivarEpithet;
    private boolean candidatus;
    private NamePart notho;
    private Authorship combinationAuthorship;
    private Authorship basionymAuthorship;
    private String sanctioningAuthor;
    private NomCode code;
    private NomStatus nomStatus;
    private Boolean originalSpelling;
    private Boolean genderAgreement;
    private Gender gender;
    private String publishedInId;
    private String publishedInPage;
    private String publishedInPageLink;
    private Integer publishedInYear;
    private Origin origin;
    private NameType type;
    private List<Identifier> identifier;
    private URI link;
    private String nomenclaturalNote;
    private String unparsed;
    private String remarks;

    private Builder() {
    }

    public Builder created(LocalDateTime val) {
      created = val;
      return this;
    }

    public Builder createdBy(Integer val) {
      createdBy = val;
      return this;
    }

    public Builder modified(LocalDateTime val) {
      modified = val;
      return this;
    }

    public Builder modifiedBy(Integer val) {
      modifiedBy = val;
      return this;
    }

    public Builder datasetKey(Integer val) {
      datasetKey = val;
      return this;
    }

    public Builder id(String val) {
      id = val;
      return this;
    }

    public Builder sectorKey(Integer val) {
      sectorKey = val;
      return this;
    }

    public Builder verbatimKey(Integer val) {
      verbatimKey = val;
      return this;
    }

    public Builder namesIndexId(Integer val) {
      namesIndexId = val;
      return this;
    }

    public Builder namesIndexType(MatchType val) {
      namesIndexType = val;
      return this;
    }

    public Builder scientificName(String val) {
      scientificName = val;
      return this;
    }

    public Builder authorship(String val) {
      authorship = val;
      return this;
    }

    public Builder rank(Rank val) {
      rank = val;
      return this;
    }

    public Builder uninomial(String val) {
      uninomial = val;
      return this;
    }

    public Builder genus(String val) {
      genus = val;
      return this;
    }

    public Builder infragenericEpithet(String val) {
      infragenericEpithet = val;
      return this;
    }

    public Builder specificEpithet(String val) {
      specificEpithet = val;
      return this;
    }

    public Builder infraspecificEpithet(String val) {
      infraspecificEpithet = val;
      return this;
    }

    public Builder cultivarEpithet(String val) {
      cultivarEpithet = val;
      return this;
    }

    public Builder candidatus(boolean val) {
      candidatus = val;
      return this;
    }

    public Builder notho(NamePart val) {
      notho = val;
      return this;
    }

    public Builder combinationAuthorship(Authorship val) {
      combinationAuthorship = val;
      return this;
    }

    public Builder basionymAuthorship(Authorship val) {
      basionymAuthorship = val;
      return this;
    }

    public Builder sanctioningAuthor(String val) {
      sanctioningAuthor = val;
      return this;
    }

    public Builder code(NomCode val) {
      code = val;
      return this;
    }

    public Builder nomStatus(NomStatus val) {
      nomStatus = val;
      return this;
    }

    public Builder originalSpelling(Boolean val) {
      originalSpelling = val;
      return this;
    }

    public Builder genderAgreement(Boolean val) {
      genderAgreement = val;
      return this;
    }

    public Builder gender(Gender val) {
      gender = val;
      return this;
    }

    public Builder publishedInId(String val) {
      publishedInId = val;
      return this;
    }

    public Builder publishedInPage(String val) {
      publishedInPage = val;
      return this;
    }

    public Builder publishedInPageLink(String val) {
      publishedInPageLink = val;
      return this;
    }

    public Builder publishedInYear(Integer val) {
      publishedInYear = val;
      return this;
    }

    public Builder origin(Origin val) {
      origin = val;
      return this;
    }

    public Builder type(NameType val) {
      type = val;
      return this;
    }

    public Builder identifier(List<Identifier> identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder link(URI val) {
      link = val;
      return this;
    }

    public Builder nomenclaturalNote(String val) {
      nomenclaturalNote = val;
      return this;
    }

    public Builder unparsed(String val) {
      unparsed = val;
      return this;
    }

    public Builder remarks(String val) {
      remarks = val;
      return this;
    }

    public Name build() {
      return new Name(this);
    }
  }
}
