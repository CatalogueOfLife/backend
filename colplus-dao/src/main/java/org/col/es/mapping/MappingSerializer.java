package org.col.es.mapping;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Serializes a {@link Mapping} instance to JSON, which is then used to actually create the document
 * type mapping within Elasticsearch. N.B. The way we serialize Mapping instances may (and probably
 * will) diverge from the way we serialize model objects. Therefore we don't use the ApiModule
 * class.
 */
public class MappingSerializer<T> {

  public static final ObjectMapper OBJECT_MAPPER = configureMapper();

  private final Mapping<T> mapping;
  private final boolean pretty;

  public MappingSerializer(Mapping<T> mapping) {
    this(mapping, false);
  }

  public MappingSerializer(Mapping<T> mapping, boolean pretty) {
    this.mapping = mapping;
    this.pretty = pretty;
  }

  public Map<String, Object> asMap() {
    return OBJECT_MAPPER.convertValue(mapping, new TypeReference<Map<String, Object>>() {});
  }

  public String serialize() {
    try {
      if (pretty) {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(mapping);
      }
      return OBJECT_MAPPER.writeValueAsString(mapping);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
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
