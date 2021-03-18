package life.catalogue.importer.coldp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.jackson.FastutilsSerde;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.License;
import life.catalogue.importer.dwca.EmlParser;
import life.catalogue.importer.jackson.EnumParserSerde;
import life.catalogue.parser.CountryParser;
import life.catalogue.parser.LicenseParser;
import org.gbif.dwc.terms.TermFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * ColDP metadata parser that falls back to EML if no YAML metadata is found.
 */
public class MetadataParser {
  private static final Logger LOG = LoggerFactory.getLogger(MetadataParser.class);
  private static final List<String> METADATA_FILENAMES = ImmutableList.of("metadata.yaml", "metadata.yml");
  private static final List<String> EML_FILENAMES = ImmutableList.of("eml.xml", "metadata.xml");
  private static final ObjectReader DATASET_YAML_READER;
  private static final ObjectMapper OM;
  static {
    OM = new ObjectMapper(new YAMLFactory())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JavaTimeModule())
        .registerModule(new ColdpMetadataModule());
    DATASET_YAML_READER = OM.readerFor(YamlDataset.class);
    TermFactory.instance().registerTerm(ColdpInserter.CSLJSON_CLASS_TERM);
  }
  public static  class ColdpMetadataModule extends SimpleModule {
    public ColdpMetadataModule() {
      super("ColdpYaml");
      EnumParserSerde<License> lserde = new EnumParserSerde<License>(LicenseParser.PARSER);
      addDeserializer(License.class, lserde.new Deserializer());
      addDeserializer(IntSet.class, new FastutilsSerde.SetDeserializer());
      addDeserializer(Country.class, new JsonDeserializer<Country>(){
        @Override
        public Country deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
          return CountryParser.PARSER.parseOrNull(jp.getText());
        }
      });
    }
    
    @Override
    public void setupModule(SetupContext ctxt) {
      // default enum serde
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      super.setupModule(ctxt);
    }
  }

  static class YamlDataset extends DatasetWithSettings {

    @JsonProperty("contact")
    public void setContactList(List<Person> contacts) {
      if (contacts == null || contacts.isEmpty()) {
        setContact(null);
      } else {
        setContact(contacts.get(0));
      }
    }

    @JsonProperty("authorsAndEditors")
    public void setAuthorsAndEditors(List<Person> authorsAndEditors) {
      if (authorsAndEditors == null || authorsAndEditors.isEmpty()) {
        setAuthors(Collections.emptyList());
      } else {
        setAuthors(authorsAndEditors);
      }
    }

    @JsonProperty("taxonomicScope")
    public void setTaxonomicScope(String scope) {
      setGroup(scope);
    }

  }

  /**
   * Reads the dataset metadata.yaml or metadata.yml from a given folder.
   * In case of parsing errors an empty optional is returned.
   */
  public static Optional<DatasetWithSettings> readMetadata(Path dir) {
    for (String fn : METADATA_FILENAMES) {
      Path metapath = dir.resolve(fn);
      if (Files.exists(metapath)) {
        try {
          return readMetadata(Files.newInputStream(metapath));
        } catch (Exception e) {
          LOG.error("Error reading metadata from " + fn, e);
        }
      }
    }
    // also try with EML if none is found
    for (String fn : EML_FILENAMES) {
      Path eml = dir.resolve(fn);
      if (Files.exists(eml)) {
        try {
          return EmlParser.parse(eml);
        } catch (IOException e) {
          LOG.error("Error reading EML file " + fn, e);
        }
      }
    }

    return Optional.empty();
  }
  
  public static Optional<DatasetWithSettings> readMetadata(InputStream stream) throws IOException {
    if (stream != null) {
      DatasetWithSettings d = DATASET_YAML_READER.readValue(stream);
      if (d.getDescription() != null) {
        d.setDescription(d.getDescription().trim());
      }
      return Optional.of(d);
  }
    return Optional.empty();
  }

}
