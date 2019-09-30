package org.col.es.ddl;

import org.col.es.mapping.Mappings;

public class Index7Definition extends IndexDefinition<Mappings> {

  public Index7Definition(Settings settings, Mappings mappings) {
    super(settings, mappings);
  }

  @Override
  public Mappings createMappings(Mappings mappings) {
    return mappings;
  }

}
