package life.catalogue.api.jackson;

import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;

/**
 * A simple jackson property filter that ignore all properties that start with "label".
 * Used to suppress name labels if they are part of a compount name usage which already has a label.
 */
public class LabelPropertyFilter  extends SimpleBeanPropertyFilter {
  public static final String NAME = "labelFilter";

  @Override
  protected boolean include(PropertyWriter writer) {
    return !writer.getName().startsWith("label");
  }
}