package org.col.api.model;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Simplified citation class linked to an optional serial container.
 */
public class Reference extends DatasetScopedEntity<String> implements VerbatimEntity {
  
  private Integer sectorKey;
  private Integer verbatimKey;
  
  /**
   * Reference metadata encoded as CSL-JSON.
   */
  private CslData csl;
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Reference reference = (Reference) o;
    return Objects.equals(sectorKey, reference.sectorKey) &&
        Objects.equals(verbatimKey, reference.verbatimKey) &&
        Objects.equals(csl, reference.csl) &&
        Objects.equals(citation, reference.citation) &&
        Objects.equals(year, reference.year);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, verbatimKey, csl, citation, year);
  }
  
  /**
   * The citation generated from the CSL data or the verbatim citation if it could not be parsed
   * into a structured CSLData object.
   */
  @Nonnull
  private String citation;
  
  /**
   * Parsed integer of the year of publication. Extracted from CSL data, but kept separate to allow
   * sorting on int order.
   */
  private Integer year;
  
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
  
  public CslData getCsl() {
    return csl;
  }
  
  public void setCsl(CslData csl) {
    this.csl = csl;
  }
  
  public String getCitation() {
    return citation;
  }
  
  public void setCitation(String citation) {
    this.citation = citation;
  }
  
  public Integer getYear() {
    return year;
  }
  
  public void setYear(Integer year) {
    this.year = year;
  }
  
  public boolean isParsed() {
    return csl != null;
  }
  
  
  @Override
  public String toString() {
    return "Reference{" + "id='" + getId() + '\'' + ", csl='" + csl + '\'' + '}';
  }
  
  /**
   * Sets the exact page in the underlying CSL item. We add this delegation method as we keep the
   * page separate from the rest of the citation in our data model.
   *
   * @param page
   */
  public void setPage(String page) {
    if (csl == null) {
      csl = new CslData();
    }
    csl.setPage(page);
  }
  
  /**
   * Gets the exact page from the underlying CSL item. We add this delegation method as we keep the
   * page separate from the rest of the citation in our data model.
   */
  public String getPage() {
    return csl == null ? null : csl.getPage();
  }
  
}
