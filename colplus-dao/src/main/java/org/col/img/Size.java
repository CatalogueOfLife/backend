package org.col.img;

import java.util.Objects;
import javax.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Size {
  
  private int width;
  private int height;
  
  @JsonCreator
  public Size(@JsonProperty("width") @Min(10) int width, @JsonProperty("height") @Min(10) int height) {
    this.width = width;
    this.height = height;
  }
  
  public int getWidth() {
    return width;
  }
  
  public int getHeight() {
    return height;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Size size = (Size) o;
    return width == size.width &&
        height == size.height;
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(width, height);
  }
}
