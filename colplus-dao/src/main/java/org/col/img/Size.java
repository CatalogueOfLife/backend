package org.col.img;

import java.util.Objects;
import javax.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Size {
  
  private int height;
  private int width;
  
  @JsonCreator
  public Size(@JsonProperty("height") @Min(10) int height, @JsonProperty("width") @Min(10) int width) {
    this.height = height;
    this.width = width;
  }
  
  public int getHeight() {
    return height;
  }
  
  public int getWidth() {
    return width;
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
