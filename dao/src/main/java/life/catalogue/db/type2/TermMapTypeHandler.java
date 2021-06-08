package life.catalogue.db.type2;

import org.gbif.dwc.terms.Term;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Postgres type handler converting an map of term values into a postgres JSONB data type.
 */
public class TermMapTypeHandler extends JsonAbstractHandler<Map<Term, String>> {

  public TermMapTypeHandler() {
    super("term map", new TypeReference<Map<Term, String>>() {});
  }
  
}
