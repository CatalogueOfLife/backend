package org.col.es.ddl;

public abstract class IndexDefinition<T> {

  private IndexSettings settings;
  private T mappings;

  public IndexSettings getSettings() {
    return settings;
  }

  public void setSettings(IndexSettings settings) {
    this.settings = settings;
  }

  public T getMappings() {
    return mappings;
  }

  public void setMappings(T mappings) {
    this.mappings = mappings;
  }

  public abstract T createMappings(DocumentTypeMapping typeMapping);

}
