package org.col.api;

import java.util.Objects;

/**
 *
 */
public class Serial {
  private Integer key;
  private String title;

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Serial serial = (Serial) o;
    return Objects.equals(key, serial.key) &&
        Objects.equals(title, serial.title);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, title);
  }
}
