package org.col.es.query;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractQuery implements Query {

  public static final ObjectMapper om = configureMapper();

  private static ObjectMapper configureMapper() {
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    om.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    om.setSerializationInclusion(Include.NON_NULL);
    return om;
  }

  public String toString() {
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
