package life.catalogue.api.model;

import java.util.Objects;

public class TaxonProperty extends DatasetScopedEntity<Integer> implements ExtensionEntity {

  private Integer sectorKey;
  private Sector.Mode sectorMode;
  private Integer verbatimKey;
  private Integer verbatimSourceKey;
  private String property;
  private String value;
  private String referenceId;
  private String page;
  private Integer ordinal;
  private String remarks;

  @Override
  public Integer getSectorKey() {
    return sectorKey;
  }

  @Override
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  @Override
  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  @Override
  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  public Integer getVerbatimSourceKey() {
    return verbatimSourceKey;
  }

  public void setVerbatimSourceKey(Integer verbatimSourceKey) {
    this.verbatimSourceKey = verbatimSourceKey;
  }

  public String getProperty() {
    return property;
  }

  public void setProperty(String property) {
    this.property = property;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getPage() {
    return page;
  }

  public void setPage(String page) {
    this.page = page;
  }

  public Integer getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(Integer ordinal) {
    this.ordinal = ordinal;
  }

  @Override
  public String getReferenceId() {
    return referenceId;
  }
  
  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
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
    if (!(o instanceof TaxonProperty)) return false;
    if (!super.equals(o)) return false;

    TaxonProperty that = (TaxonProperty) o;
    return Objects.equals(sectorKey, that.sectorKey) &&
      sectorMode == that.sectorMode &&
      Objects.equals(verbatimKey, that.verbatimKey) &&
      Objects.equals(verbatimSourceKey, that.verbatimSourceKey) &&
      Objects.equals(property, that.property) &&
      Objects.equals(value, that.value) &&
      Objects.equals(referenceId, that.referenceId) &&
      Objects.equals(page, that.page) &&
      Objects.equals(ordinal, that.ordinal) &&
      Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, sectorMode, verbatimKey, verbatimSourceKey, property, value, referenceId, page, ordinal, remarks);
  }
}
