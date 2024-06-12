package life.catalogue.dw.jersey.provider;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * Jersey parameter converter & provider that uses our jackson Mapper
 * to serde enums.
 */
@Provider
public class TermParamConverterProvider implements ParamConverterProvider {
  private final static TermFactory TF = TermFactory.instance();

  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] antns) {
    if (rawType == Term.class) {
      return new TermParamConverter();
    }
    return null;
  }
  
  static class TermParamConverter<T> implements ParamConverter<Term> {
    private static final Logger LOG = LoggerFactory.getLogger(TermParamConverter.class);
    
    @Override
    public Term fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      try {
        // we need to quote the value so it looks like a json value
        return TF.findTerm(value);
        
      } catch (IllegalArgumentException e) {
        LOG.debug("Failed to lookup {} Term", value, e);
        throw e;
      }
    }
    
    @Override
    public String toString(Term value) {
      if (value == null) return null;
      return value.prefixedName();
    }
  }
}
