package life.catalogue.dw.jersey.provider;

import life.catalogue.api.jackson.UUIDSerde;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.UUID;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import com.google.common.base.Strings;

/**
 * Jersey parameter converter & provider that uses our jackson Mapper
 * to serde enums.
 */
@Provider
public class UUIDParamConverterProvider implements ParamConverterProvider {

  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] antns) {
    if (rawType == UUID.class) {
      return new UUIDParamConverter();
    }
    return null;
  }
  
  static class UUIDParamConverter<T> implements ParamConverter<UUID> {

    @Override
    public UUID fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      return UUIDSerde.from(value);
    }
    
    @Override
    public String toString(UUID value) {
      if (value == null) return null;
      return UUIDSerde.to(value);
    }
  }
}
