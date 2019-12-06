package life.catalogue.db.type2;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * Postgres type handler converting an map of object values into a postgres JSONB data type.
 */
public class StringMapTypeHandler extends JsonAbstractHandler<Map<String, String>> {

  public StringMapTypeHandler() {
    super("map", new TypeReference<Map<String, String>>() {});
  }
  
}
