package org.col.es.ddl;

import org.col.es.mapping.Mappings;

/**
 * The outer-most object for the Create Index API. It consists of an object configuring the index and the document type
 * mapping. In ES7 the type mapping is essentially just part of the way the index is defined. ES6 maintains some
 * backward compatibility to times when you could have multiple document types (and hence mappings) within one and the
 * same index. This is expressed in the two subclasses {@link Index6Definition} and {@link Index7Definition}.
 * 
 * @param <T> An object specifying the type mapping
 */
public abstract class IndexDefinition<T> {

  private Settings settings;
  private T mappings;

  public IndexDefinition(Settings settings, Mappings mappings) {
    this.settings = settings;
    this.mappings = createMappings(mappings);
  }

  public Settings getSettings() {
    return settings;
  }

  public T getMappings() {
    return mappings;
  }

  public abstract T createMappings(Mappings mappings);

}
