package org.col.dw.jersey;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.col.api.jackson.ApiModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Jersey parameter converter & provider that uses our jackson Mapper
 * to serde enums.
 */
@Provider
public class EnumParamConverterProvider implements ParamConverterProvider {

  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] antns) {
    if (!rawType.isEnum()) {
      return null;
    }
    return new EnumParamConverter<T>(rawType);
  }

  static class EnumParamConverter<T> implements ParamConverter<T> {
    private static final Logger LOG = LoggerFactory.getLogger(EnumParamConverter.class);
    private final Class<T> type;

    EnumParamConverter(Class<T> type) {
      this.type = type;
    }

    @Override
    public T fromString(String value) {
      try {
        return ApiModule.MAPPER.readValue(value, type);
      } catch (IOException e) {
        LOG.debug("Failed to convert {} into {}", value, type, e);
        throw new IllegalArgumentException("Invalid "+type.getSimpleName()+" value: " + value);
      }
    }

    @Override
    public String toString(T value) {
      if (value == null) return null;
      try {
        return ApiModule.MAPPER.writeValueAsString(value);

      } catch (JsonProcessingException e) {
        throw new WebApplicationException("Failed to serialize "+type.getSimpleName()+" value "+value, e);
      }
    }
  }
}
