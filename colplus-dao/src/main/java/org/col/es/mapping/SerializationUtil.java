package org.col.es.mapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Serialization utils for Elasticsearch document type mappings.
 * 
 */
public class SerializationUtil {

  // Mapper for (de)serializing document type mappings
  public static final ObjectMapper MAPPER = configureMapper();

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF =
      new TypeReference<Map<String, Object>>() {};

  public static Map<String, Object> readIntoMap(InputStream is) {
    try {
      return MAPPER.readValue(is, MAP_TYPE_REF);
    } catch (IOException e) {
      throw new MappingException(e);
    }
  }

  public static String serialize(Map<String, Object> map) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    } catch (JsonProcessingException e) {
      throw new MappingException(e);
    }
  }

  public static String serialize(Mapping<?> mapping) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(mapping);
    } catch (JsonProcessingException e) {
      throw new MappingException(e);
    }
  }

  private static ObjectMapper configureMapper() {
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    om.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    om.setSerializationInclusion(Include.NON_NULL);
    om.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    return om;
  }
}
