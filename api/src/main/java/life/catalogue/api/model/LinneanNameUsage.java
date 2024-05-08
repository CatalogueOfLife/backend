package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.*;

import java.util.Objects;

/**
 * Stripped down NameUsageBase version with just the name properties and the usage id and parentID
 */
public class LinneanNameUsage implements FormattableName, NameUsageCore {
  private String id; // usage id, not name!
  private String parentId;
  private Integer sectorKey;
  private TaxonomicStatus status;
  private String nameId;
  private NameType type;
  private NomCode code;
  private String scientificName;
  private String authorship;
  private Rank rank;
  private String uninomial;
  private String genus;
  private String infragenericEpithet;
  private String specificEpithet;
  private String infraspecificEpithet;
  private String cultivarEpithet;
  private ExAuthorship combinationAuthorship = new ExAuthorship();
  private ExAuthorship basionymAuthorship = new ExAuthorship();
  private Authorship emendAuthorship = new Authorship();
  private String sanctioningAuthor;

  public LinneanNameUsage() {
  }

  public LinneanNameUsage(NameUsageBase nu) {
    id = nu.getId();
    parentId = nu.getParentId();
    sectorKey = nu.getSectorKey();
    status = nu.getStatus();
    nameId = nu.getName().getId();
    type = nu.getName().getType();
    code = nu.getName().getCode();
    scientificName = nu.getName().getScientificName();
    authorship = nu.getName().getAuthorship();
    rank = nu.getName().getRank();
    uninomial = nu.getName().getUninomial();
    genus = nu.getName().getGenus();
    infragenericEpithet = nu.getName().getInfragenericEpithet();
    specificEpithet = nu.getName().getSpecificEpithet();
    infraspecificEpithet = nu.getName().getInfraspecificEpithet();
    cultivarEpithet = nu.getName().getCultivarEpithet();
    combinationAuthorship = nu.getName().getCombinationAuthorship();
    basionymAuthorship = nu.getName().getBasionymAuthorship();
    emendAuthorship = nu.getName().getEmendAuthorship();
    sanctioningAuthor = nu.getName().getSanctioningAuthor();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public String getNameId() {
    return nameId;
  }

  public void setNameId(String nameId) {
    this.nameId = nameId;
  }

  public NameType getType() {
    return type;
  }

  public void setType(NameType type) {
    this.type = type;
  }

  @Override
  public NomCode getCode() {
    return code;
  }

  @Override
  public void setCode(NomCode code) {
    this.code = code;
  }

  @Override
  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  @Override
  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }

  @Override
  public Rank getRank() {
    return rank;
  }

  @Override
  public void setRank(Rank rank) {
    this.rank = rank;
  }

  @Override
  public String getUninomial() {
    return uninomial;
  }

  @Override
  public void setUninomial(String uninomial) {
    this.uninomial = uninomial;
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
  public String getInfragenericEpithet() {
    return infragenericEpithet;
  }

  @Override
  public void setInfragenericEpithet(String infragenericEpithet) {
    this.infragenericEpithet = infragenericEpithet;
  }

  @Override
  public String getSpecificEpithet() {
    return specificEpithet;
  }

  @Override
  public void setSpecificEpithet(String specificEpithet) {
    this.specificEpithet = specificEpithet;
  }

  @Override
  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }

  @Override
  public void setInfraspecificEpithet(String infraspecificEpithet) {
    this.infraspecificEpithet = infraspecificEpithet;
  }

  @Override
  public NamePart getNotho() {
    return null;
  }

  @Override
  public void setNotho(NamePart namePart) {

  }

  public String getCultivarEpithet() {
    return cultivarEpithet;
  }

  public void setCultivarEpithet(String cultivarEpithet) {
    this.cultivarEpithet = cultivarEpithet;
  }

  @Override
  public ExAuthorship getCombinationAuthorship() {
    return combinationAuthorship;
  }

  public void setCombinationAuthorship(ExAuthorship combinationAuthorship) {
    this.combinationAuthorship = combinationAuthorship;
  }

  @Override
  public ExAuthorship getBasionymAuthorship() {
    return basionymAuthorship;
  }

  public void setBasionymAuthorship(ExAuthorship basionymAuthorship) {
    this.basionymAuthorship = basionymAuthorship;
  }

  @Override
  public Authorship getEmendAuthorship() {
    return emendAuthorship;
  }

  public void setEmendAuthorship(Authorship emendAuthorship) {
    this.emendAuthorship = emendAuthorship;
  }

  public String getSanctioningAuthor() {
    return sanctioningAuthor;
  }

  public void setSanctioningAuthor(String sanctioningAuthor) {
    this.sanctioningAuthor = sanctioningAuthor;
  }

  @Override
  public String getLabel() {
    StringBuilder sb = new StringBuilder();
    if (scientificName != null) {
      sb.append(scientificName);
    }
    if (authorship != null) {
      sb.append(" ");
      sb.append(authorship);
    }
    return sb.toString();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LinneanNameUsage)) return false;
    LinneanNameUsage that = (LinneanNameUsage) o;
    return Objects.equals(id, that.id)
           && Objects.equals(parentId, that.parentId)
           && Objects.equals(sectorKey, that.sectorKey)
           && status == that.status
           && Objects.equals(nameId, that.nameId)
           && type == that.type
           && code == that.code
           && Objects.equals(scientificName, that.scientificName)
           && Objects.equals(authorship, that.authorship)
           && rank == that.rank
           && Objects.equals(uninomial, that.uninomial)
           && Objects.equals(genus, that.genus)
           && Objects.equals(infragenericEpithet, that.infragenericEpithet)
           && Objects.equals(specificEpithet, that.specificEpithet)
           && Objects.equals(infraspecificEpithet, that.infraspecificEpithet)
           && Objects.equals(cultivarEpithet, that.cultivarEpithet)
           && Objects.equals(combinationAuthorship, that.combinationAuthorship)
           && Objects.equals(basionymAuthorship, that.basionymAuthorship)
           && Objects.equals(emendAuthorship, that.emendAuthorship)
           && Objects.equals(sanctioningAuthor, that.sanctioningAuthor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, parentId, sectorKey, status, nameId, type, code, scientificName, authorship, rank, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet, combinationAuthorship, basionymAuthorship, emendAuthorship, sanctioningAuthor);
  }

  @Override
  public String toString() {
    // output similar to SimpleName
    StringBuilder sb = new StringBuilder();
    if (status != null) {
      sb.append(status);
      sb.append(" ");
    }
    if (rank != null) {
      sb.append(rank);
      sb.append(" ");
    }
    sb.append(getLabel());
    if (id != null || parentId != null) {
      sb.append(" [");
      if (id != null) {
        sb.append(id);
      }
      if (parentId != null) {
        sb.append(" parentId=");
        sb.append(parentId);
      }
      sb.append("]");
    }
    return sb.toString();
  }
}
