package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.TypeStatus;

import java.net.URI;
import java.util.Objects;

/**
 * Type material should only be associated with the original name, not with a recombination.
 */
public class TypeMaterial extends DatasetScopedEntity<String> implements VerbatimEntity, Referenced {

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

    private String locality;

    private Country country;

    /**
     * WGS84
     */
    private Double latitude;

    /**
     * WGS84
     */
    private Double longitude;

    /**
     * In meters above mean sea level.
     */
    private Integer altitude;
    private String host;
    private String date;
    private String collector;

    /**
     * Any informal note about the type.
     */
    private String remarks;

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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getAltitude() {
        return altitude;
    }

    public void setAltitude(Integer altitude) {
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
        if (!super.equals(o)) return false;
        TypeMaterial that = (TypeMaterial) o;
        return Objects.equals(sectorKey, that.sectorKey) &&
            Objects.equals(verbatimKey, that.verbatimKey) &&
            Objects.equals(nameId, that.nameId) &&
            Objects.equals(citation, that.citation) &&
            status == that.status &&
            Objects.equals(referenceId, that.referenceId) &&
            Objects.equals(link, that.link) &&
            Objects.equals(locality, that.locality) &&
            country == that.country &&
            Objects.equals(latitude, that.latitude) &&
            Objects.equals(longitude, that.longitude) &&
            Objects.equals(altitude, that.altitude) &&
            Objects.equals(host, that.host) &&
            Objects.equals(date, that.date) &&
            Objects.equals(collector, that.collector) &&
            Objects.equals(remarks, that.remarks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sectorKey, verbatimKey, nameId, citation, status, referenceId, link, locality, country, latitude, longitude, altitude, host, date, collector, remarks);
    }
}
