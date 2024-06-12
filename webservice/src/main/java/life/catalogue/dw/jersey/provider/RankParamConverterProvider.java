package life.catalogue.dw.jersey.provider;

import life.catalogue.parser.RankParser;

import org.gbif.nameparser.api.Rank;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import com.google.common.base.Strings;

/**
 * Jersey parameter converter & provider that uses our jackson Mapper
 * to serde enums.
 */
@Provider
public class RankParamConverterProvider implements ParamConverterProvider {

  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] antns) {
    if (rawType == Rank.class) {
      return new RankParamConverter();
    }
    return null;
  }

  static class RankParamConverter<T> implements ParamConverter<Rank> {

    @Override
    public Rank fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      return CodeParamConverterProvider.parseOrThrowIAE(value, RankParser.PARSER, Rank.class);
    }
    
    @Override
    public String toString(Rank value) {
      if (value == null) return null;
      return value.name().toLowerCase();
    }
  }
}
