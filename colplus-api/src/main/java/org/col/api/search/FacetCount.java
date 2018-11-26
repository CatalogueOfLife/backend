package org.col.api.search;

public class FacetCount {
  
  private String value;
  private int count;
  
  public FacetCount() {
  }
  
  public FacetCount(String value, int count) {
    this.value = value;
    this.count = count;
  }
  
  public String getValue() {
    return value;
  }
  
  public void setValue(String value) {
    this.value = value;
  }
  
  public int getCount() {
    return count;
  }
  
  public void setCount(int count) {
    this.count = count;
  }
}
