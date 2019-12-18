package life.catalogue.es.ddl;

import java.util.Map;
import life.catalogue.es.mapping.Mappings;

/**
 * The outer-most object for the Create Index API and Get Index API. It consists of an object configuring the index and the document type mapping.
 */
public class IndexDefinition {

  private Settings settings;
  private Mappings mappings;
  private Map<String, AliasDefinition> aliases;

  public IndexDefinition() {}

  public IndexDefinition(Settings settings, Mappings mappings) {
    this.settings = settings;
    this.mappings = mappings;
  }

  public Settings getSettings() {
    return settings;
  }

  public Mappings getMappings() {
    return mappings;
  }

  public void setMappings(Mappings mappings) {
    this.mappings = mappings;
  }

  public Map<String, AliasDefinition> getAliases() {
    return aliases;
  }

  public void setAliases(Map<String, AliasDefinition> aliases) {
    this.aliases = aliases;
  }

}
