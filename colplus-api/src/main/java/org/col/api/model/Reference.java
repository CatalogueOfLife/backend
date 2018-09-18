package org.col.api.model;

import java.util.Objects;

/**
 * Simplified citation class linked to an optional serial container.
 */
public class Reference implements VerbatimEntity {

  /**
   * Original key as provided by the dataset.
   */
  private String id;

  /**
   * Key to dataset instance. Defines context of the reference key.
   */
  private Integer datasetKey;

  private Integer verbatimKey;

  /**
   * Reference metadata encoded as CSL-JSON.
   */
  private CslData csl;

  /**
   * The citation generated from the CSL data or the verbatim citation if it could not be parsed
   * into a structured CSLData object.
   */
  private String citation;

  /**
   * Parsed integer of the year of publication. Extracted from CSL data, but kept separate to allow
   * sorting on int order.
   */
  private Integer year;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
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
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Reference reference = (Reference) o;
    return Objects.equals(id, reference.id)
        && Objects.equals(datasetKey, reference.datasetKey) && Objects.equals(csl, reference.csl)
        && Objects.equals(citation, reference.citation) && Objects.equals(year, reference.year);
  }

  @Override
  public int hashCode() {

    return Objects.hash(id, datasetKey, csl, year);
  }

  @Override
  public String toString() {
    return "Reference{" + "id='" + id + '\'' + ", csl='" + csl + '\'' + '}';
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
