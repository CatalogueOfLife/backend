package life.catalogue.dw.jersey.provider;

import life.catalogue.api.jackson.ApiModule;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.regex.Pattern;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;

/**
 * Jersey parameter converter & provider that uses our jackson Mapper
 * to serde enums.
 */
abstract class AbstractJacksonConverterProvider<J> implements ParamConverterProvider {
  private final Class<J> type;

  public AbstractJacksonConverterProvider(Class<J> type) {
    this.type = type;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] antns) {
    if (rawType == type) {
      return (ParamConverter<T>) new ApiParamConverter(type);
    }
    return null;
  }

  static class ApiParamConverter<J> implements ParamConverter<J> {
    private static final Pattern REMOVE_QUOTES = Pattern.compile("^\"|\"$");
    private static final Logger LOG = LoggerFactory.getLogger(ApiParamConverter.class);
    private final ObjectReader reader;
    private final ObjectWriter writer;
    protected final Class<J> type;

    public ApiParamConverter(Class<J> type) {
      this.type = type;
      reader = ApiModule.MAPPER.readerFor(type);
      writer = ApiModule.MAPPER.writerFor(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public J fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      // we need to quote the value so it looks like a json value
      try {
        return reader.readValue('"' + value.trim() + '"');
      } catch (IOException e) {
        LOG.debug("Failed to convert {} into {}", value, type, e);
        throw new BadRequestException("Invalid " + type.getSimpleName() + " value: " + value);
      }
    }
    
    @Override
    public String toString(J value) {
      if (value == null) return null;
      try {
        String json = writer.writeValueAsString(value);
        return REMOVE_QUOTES.matcher(json).replaceAll("");
        
      } catch (JsonProcessingException e) {
        throw new WebApplicationException("Failed to serialize " + type.getSimpleName() + " value " + value, e);
      }
    }
  }
}
