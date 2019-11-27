package life.catalogue.es.ddl;

import java.util.Collections;
import java.util.Map;

import life.catalogue.es.mapping.Mappings;

public class Index6Definition extends IndexDefinition<Map<String, Mappings>> {

  public Index6Definition(Settings settings, Mappings mappings) {
    super(settings, mappings);
  }

  @Override
  public Map<String, Mappings> createMappings(Mappings mappings) {
    return Collections.singletonMap("_doc", mappings);
  }

}
