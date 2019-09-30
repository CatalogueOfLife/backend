package org.col.es.ddl;

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

import org.col.es.EsException;
import org.col.es.mapping.MappingException;

/**
 * JSON utils for the Create Index API (see {@link IndexDefinition}). Most important difference with serializing API
 * model classes is that fields rather than getters/setters are serialized and enums are serialized using toString().
 */
public class JsonUtil {

  // Mapper for (de)serializing DDL (IndexDefinition instances and document type mappings)
  public static final ObjectMapper MAPPER = configureMapper();

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {};

  public static Map<String, Object> readIntoMap(InputStream is) {
    try {
      return MAPPER.readValue(is, MAP_TYPE_REF);
    } catch (IOException e) {
      throw new MappingException(e);
    }
  }

  public static String serialize(Object obj) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

  public static <T> T deserialize(InputStream is, Class<T> cls) {
    try {
      return MAPPER.readValue(is, cls);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  public static String pretty(Object obj) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new MappingException(e);
    }
  }

  private static ObjectMapper configureMapper() {
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    om.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    om.setSerializationInclusion(Include.NON_EMPTY);
    om.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    return om;
  }
}
