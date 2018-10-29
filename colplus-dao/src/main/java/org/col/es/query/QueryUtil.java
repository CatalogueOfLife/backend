package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

class QueryUtil {

  private static final ObjectMapper om = configureMapper();

  static String toString(Object obj) {
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static ObjectMapper configureMapper() {
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    om.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    om.setSerializationInclusion(Include.NON_NULL);
    om.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
    return om;
  }

}
