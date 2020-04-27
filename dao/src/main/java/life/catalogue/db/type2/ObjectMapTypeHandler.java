package life.catalogue.db.type2;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

public class ObjectMapTypeHandler extends JsonAbstractHandler<Map<String, Object>> {

  public ObjectMapTypeHandler() {
    super("term map", new TypeReference<Map<String, Object>>() {});
  }
  
}
