package org.col.es.ddl;

import java.util.Collections;
import java.util.Map;

public class Index6Definition extends IndexDefinition<Map<String, DocumentTypeMapping>> {

  @Override
  public Map<String, DocumentTypeMapping> createMappings(DocumentTypeMapping typeMapping) {
    return Collections.singletonMap("_doc", typeMapping);
  }

}
