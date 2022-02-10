package life.catalogue.dw.jersey.provider;

import life.catalogue.api.util.VocabularyUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import com.google.common.base.Strings;

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
  
  static class EnumParamConverter<T> extends AbstractJacksonConverterProvider.ApiParamConverter<T> {

    EnumParamConverter(Class<T> type) {
      super(type);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      // first try raw enum value without ApiModule transformations
      Optional<T> eVal = (Optional<T>) VocabularyUtils.lookup(value, (Class<Enum>) type);
      // if not use jackson
      return eVal.orElseGet(() -> super.fromString(value));
    }

  }
}
