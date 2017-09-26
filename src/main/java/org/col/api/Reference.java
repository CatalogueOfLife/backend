package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * Simplified literature reference class linked to an optional serial container.
 */
public class Reference {

  /**
   * Internal surrogate key of the reference as provided by postgres.
   * This key is unique across all datasets but not exposed in the API.
   */
  @JsonIgnore
  private Integer key;

  /**
   * Original key as provided by the dataset.
   */
  private String id;

  /**
   * Key to dataset instance. Defines context of the reference key.
   */
  private Dataset dataset;

  /**
   * Reference metadata encoded as CSL-JSON.
   */
  private ObjectNode csl;

  /**
   * Serial container, defining the CSL container properties.
   */
  private Serial serial;

  /**
   * Parsed integer of the year of publication.
   * Extracted from CSL data, but kept separate to allow sorting on int order.
   */
  private Integer year;

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

  public Dataset getDataset() {
    return dataset;
  }

  public void setDataset(Dataset dataset) {
    this.dataset = dataset;
  }

  public ObjectNode getCsl() {
    return csl;
  }

  public void setCsl(ObjectNode csl) {
    this.csl = csl;
  }

  public Serial getSerial() {
    return serial;
  }

  public void setSerial(Serial serial) {
    this.serial = serial;
  }

  public Integer getYear() {
    return year;
  }

  public void setYear(Integer year) {
    this.year = year;
  }


  // VARIOUS METHODS DELEGATING TO THE UNDERLYING CSL JsonObject instance
  @JsonIgnore
  public String getTitle() {
    return cslStr("title");
  }

  public void setTitle(String title) {
    csl.put("title", title);
  }

  private String cslStr(String path) {
    if (csl.has(path)) {
      JsonNode node = csl.get(path);
      return node.asText();
    }
    return null;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Reference reference = (Reference) o;
    return Objects.equals(key, reference.key) &&
        Objects.equals(id, reference.id) &&
        Objects.equals(dataset, reference.dataset) &&
        Objects.equals(csl, reference.csl) &&
        Objects.equals(serial, reference.serial) &&
        Objects.equals(year, reference.year);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, dataset, csl, serial, year);
  }
}

