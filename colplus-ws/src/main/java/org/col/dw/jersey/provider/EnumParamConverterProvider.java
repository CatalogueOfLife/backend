package org.col.dw.jersey.provider;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import org.col.api.jackson.ApiModule;
import org.col.api.util.VocabularyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Pattern REMOVE_QUOTES = Pattern.compile("^\"|\"$");
    private static final Logger LOG = LoggerFactory.getLogger(EnumParamConverter.class);
    private final Class<T> type;
    
    EnumParamConverter(Class<T> type) {
      this.type = type;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      
      // first try raw enum value without ApiModule transformations
      Optional<T> eVal = (Optional<T>) VocabularyUtils.lookup(value, (Class<Enum>) type);
      // we need to quote the value so it looks like a json value
      return eVal.orElseGet(() -> {
        try {
          return ApiModule.MAPPER.readValue('"' + value.trim() + '"', type);
        } catch (IOException e) {
          LOG.debug("Failed to convert {} into {}", value, type, e);
          throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " value: " + value);
        }
      });
    }
    
    @Override
    public String toString(T value) {
      if (value == null) return null;
      try {
        String json = ApiModule.MAPPER.writeValueAsString(value);
        return REMOVE_QUOTES.matcher(json).replaceAll("");
        
      } catch (JsonProcessingException e) {
        throw new WebApplicationException("Failed to serialize " + type.getSimpleName() + " value " + value, e);
      }
    }
  }
}
