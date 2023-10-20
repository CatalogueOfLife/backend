package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Sex;
import life.catalogue.api.vocab.TypeStatus;
import life.catalogue.common.text.StringUtils;

import java.net.URI;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Type material should only be associated with the original name, not with a recombination.
 */
public class TypeMaterial extends DatasetScopedEntity<String> implements VerbatimEntity, SectorScoped, Referenced, Remarkable {

  private Integer sectorKey;
  private Integer verbatimKey;

  /**
   * The citation generated from the CSL data or the verbatim citation if it could not be parsed
   * into a structured CSLData object.
   */
  private String nameId;

  /**
   * Material citation of the type material, i.e. type specimen.
   * The citation is ideally given in the verbatim form as it was used in the original publication of the name or the subsequent designation.
   * Type material should only be associated with the original name, not with a recombination.
   */
  private String citation;

  /**
   * The status of the type material, e.g. holotype
   * Type status should only be associated with the original name, not with a recombination.
   */
  private TypeStatus status;

  /**
   * A referenceID pointing to the Reference table indicating the publication of the type designation.
   * Most often this is equivalent to the original publishedInID, but for subsequent designations the later reference can be cited.
   */
  private String referenceId;
  private URI link;
  private Country country;
  private String locality;
  private Sex sex;
  private String institutionCode;
  private String catalogNumber;
  private String associatedSequences;
  private String host;
  private String date;
  private String collector;
  private String latitude;
  private String longitude;
  private Coordinate coordinate; // exists only if parsable
  private String altitude;

  /**
   * Any informal note about the type.
   */
  private String remarks;

  /**
   * Builds an EJT compliant citation without! the type status.
   *
   * COUNTRY • specimen(s) [e.g. “1 ♂”];
   * geographic/locality data [from largest to smallest];
   * geographic coordinates;
   * altitude/elevation/depth [using alt. / elev. / m a.s.l. etc.];
   * date [format: 16 Jan. 1998];
   * collector [followed by “leg.”];
   * other collecting data [e.g. micro habitat / host / method of collecting / “DNA voucher specimen”/ “vial with detached elements”, etc.];
   * barcodes/identifiers [e.g. “GenBank: MG779236”];
   * institution code and specimen code [e.g. “CBF 06023”].
   */
  public static String buildCitation(TypeMaterial tm) {
    final String concat = "; ";
    StringBuilder sb = new StringBuilder();
    if (tm.getCountry() != null) {
      sb.append(tm.getCountry().getName().toUpperCase());
      sb.append(" • ");
    }
    if (tm.getSex() != null) {
      sb.append(tm.getSex().getSymbol());
    }
    String coord = StringUtils.concat(", ", tm.getLatitude(), tm.getLongitude());
    String specCode = StringUtils.concat(" ", tm.getInstitutionCode(), tm.getCatalogNumber());
    StringUtils.append(sb, concat, true, tm.getLocality(), coord, tm.getAltitude(),
      tm.getDate(), tm.getCollector(), tm.getHost(), tm.getRemarks(),
      tm.getAssociatedSequences(), specCode
    );
    return sb.length() > 1 ? sb.toString() : null;
  }

  @JsonIgnore
  public DSID<String> getNameKey() {
    return DSID.of(getDatasetKey(), nameId);
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

  public String getNameId() {
    return nameId;
  }

  public void setNameId(String nameId) {
    this.nameId = nameId;
  }

  public String getCitation() {
    if (citation == null) {
      citation = buildCitation(this);
    }
    return citation;
  }

  public void setCitation(String citation) {
    this.citation = citation;
  }

  public TypeStatus getStatus() {
    return status;
  }

  public void setStatus(TypeStatus status) {
    this.status = status;
  }

  public String getLocality() {
    return locality;
  }

  public void setLocality(String locality) {
    this.locality = locality;
  }

  public Country getCountry() {
    return country;
  }

  public void setCountry(Country country) {
    this.country = country;
  }

  public String getLatitude() {
    return latitude;
  }

  public void setLatitude(String latitude) {
    this.latitude = latitude;
  }

  public String getLongitude() {
    return longitude;
  }

  public void setLongitude(String longitude) {
    this.longitude = longitude;
  }

  public String getInstitutionCode() {
    return institutionCode;
  }

  public void setInstitutionCode(String institutionCode) {
    this.institutionCode = institutionCode;
  }

  public String getCatalogNumber() {
    return catalogNumber;
  }

  public void setCatalogNumber(String catalogNumber) {
    this.catalogNumber = catalogNumber;
  }

  public Sex getSex() {
    return sex;
  }

  public void setSex(Sex sex) {
    this.sex = sex;
  }

  public String getAssociatedSequences() {
    return associatedSequences;
  }

  public void setAssociatedSequences(String associatedSequences) {
    this.associatedSequences = associatedSequences;
  }

  public Coordinate getCoordinate() {
    return coordinate;
  }

  public void setCoordinate(Coordinate coordinate) {
    this.coordinate = coordinate;
  }

  public String getAltitude() {
    return altitude;
  }

  public void setAltitude(String altitude) {
    this.altitude = altitude;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public String getCollector() {
    return collector;
  }

  public void setCollector(String collector) {
    this.collector = collector;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceID) {
    this.referenceId = referenceID;
  }

  public URI getLink() {
    return link;
  }

  public void setLink(URI link) {
    this.link = link;
  }

  @Override
  public String getRemarks() {
    return remarks;
  }

  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeMaterial)) return false;
    if (!super.equals(o)) return false;
    TypeMaterial that = (TypeMaterial) o;
    return Objects.equals(sectorKey, that.sectorKey)
           && Objects.equals(verbatimKey, that.verbatimKey)
           && Objects.equals(nameId, that.nameId)
           && Objects.equals(citation, that.citation)
           && status == that.status
           && Objects.equals(referenceId, that.referenceId)
           && Objects.equals(link, that.link)
           && Objects.equals(institutionCode, that.institutionCode)
           && Objects.equals(catalogNumber, that.catalogNumber)
           && Objects.equals(locality, that.locality)
           && country == that.country
           && Objects.equals(sex, that.sex)
           && Objects.equals(associatedSequences, that.associatedSequences)
           && Objects.equals(host, that.host)
           && Objects.equals(date, that.date)
           && Objects.equals(collector, that.collector)
           && Objects.equals(latitude, that.latitude)
           && Objects.equals(longitude, that.longitude)
           && Objects.equals(coordinate, that.coordinate)
           && Objects.equals(altitude, that.altitude)
           && Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, verbatimKey, nameId, citation, status, referenceId, link, institutionCode, catalogNumber, locality, country, sex, associatedSequences, host, date, collector, latitude, longitude, coordinate, altitude, remarks);
  }
}
