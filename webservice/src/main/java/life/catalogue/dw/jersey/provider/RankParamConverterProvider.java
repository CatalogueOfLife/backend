package life.catalogue.dw.jersey.provider;

import com.google.common.base.Strings;

import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.Parser;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

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
