package org.col.api.search;

public class FacetCount {
  private String value;
  private Integer count;
  
  public FacetCount() {
  }
  
  public FacetCount(String value, Integer count) {
    this.value = value;
    this.count = count;
  }
  
  public String getValue() {
    return value;
  }
  
  public void setValue(String value) {
    this.value = value;
  }
  
  public Integer getCount() {
    return count;
  }
  
  public void setCount(Integer count) {
    this.count = count;
  }
}
