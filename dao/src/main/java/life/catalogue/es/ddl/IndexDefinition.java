package life.catalogue.es.ddl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import life.catalogue.es.EsModule;

/**
 * The outer-most object for the Create Index API and Get Index API. It consists of an object configuring the index and the document type
 * mapping.
 */
public class IndexDefinition {

  public static IndexDefinition loadDefaults() throws IOException {
    InputStream is = IndexDefinition.class.getResourceAsStream("es-settings.json");
    return EsModule.readObject(is, IndexDefinition.class);
  }

  private Settings settings;
  private Mappings mappings;
  private Map<String, AliasDefinition> aliases;

  public IndexDefinition() {}

  public Settings getSettings() {
    if (settings == null) {
      settings = new Settings();
    }
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
