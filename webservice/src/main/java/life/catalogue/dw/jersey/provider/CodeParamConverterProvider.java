package life.catalogue.dw.jersey.provider;

import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.Parser;
import life.catalogue.parser.UnparsableException;

import org.gbif.nameparser.api.NomCode;

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
public class CodeParamConverterProvider implements ParamConverterProvider {

  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] antns) {
    if (rawType == NomCode.class) {
      return new CodeParamConverter();
    }
    return null;
  }

  static <T> T parseOrThrowIAE(String value, Parser<T> parser, Class<? extends Enum> clazz) {
    try {
      return parser.parse(value).orElseThrow(() -> buildIAE(value, clazz));
    } catch (UnparsableException e) {
      throw buildIAE(value, clazz);
    }
  }

  static IllegalArgumentException buildIAE(String value, Class<? extends Enum> clazz) {
    return new IllegalArgumentException("Invalid "+clazz.getSimpleName()+": " + value);
  }

  static class CodeParamConverter<T> implements ParamConverter<NomCode> {

    @Override
    public NomCode fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      return parseOrThrowIAE(value, NomCodeParser.PARSER, NomCode.class);
    }
    
    @Override
    public String toString(NomCode value) {
      if (value == null) return null;
      return value.name();
    }
  }
}
