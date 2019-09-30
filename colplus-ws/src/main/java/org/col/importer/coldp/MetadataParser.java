package org.col.importer.coldp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import org.col.api.jackson.PermissiveEnumSerde;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.License;
import org.col.importer.jackson.EnumParserSerde;
import org.col.parser.LicenseParser;
import org.gbif.dwc.terms.TermFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataParser {
  private static final Logger LOG = LoggerFactory.getLogger(MetadataParser.class);
  private static final List<String> METADATA_FILENAMES = ImmutableList.of("metadata.yaml", "metadata.yml");
  private static final ObjectReader DATASET_READER;
  private static final ObjectMapper OM;
  static {
    OM = new ObjectMapper(new YAMLFactory())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JavaTimeModule())
        .registerModule(new ColdpYamlModule());
    DATASET_READER = OM.readerFor(Dataset.class);
    
    TermFactory.instance().registerTerm(ColdpInserter.BIBTEX_CLASS_TERM);
    TermFactory.instance().registerTerm(ColdpInserter.CSLJSON_CLASS_TERM);
  }
  private static  class ColdpYamlModule extends SimpleModule {
    public ColdpYamlModule() {
      super("ColdpYaml");
      EnumParserSerde<License> lserde = new EnumParserSerde<License>(LicenseParser.PARSER);
      addDeserializer(License.class, lserde.new Deserializer());
    }
    
    @Override
    public void setupModule(SetupContext ctxt) {
      // default enum serde
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      super.setupModule(ctxt);
    }
  }
  
  /**
   * Reads the dataset metadata.yaml or metadata.yml from a given folder
   */
  public static Optional<Dataset> readMetadata(Path dir) {
    for (String fn : METADATA_FILENAMES) {
      Path metapath = dir.resolve(fn);
      if (Files.exists(metapath)) {
        try {
          return readMetadata(Files.newInputStream(metapath));
        } catch (IOException e) {
          LOG.error("Error reading " + fn, e);
        }
      }
    }
    return Optional.empty();
  }
  
  public static Optional<Dataset> readMetadata(InputStream stream) {
    if (stream != null) {
      try {
        Dataset d = DATASET_READER.readValue(stream);
        d.setDataFormat(DataFormat.COLDP);
        if (d.getDescription() != null) {
          d.setDescription(d.getDescription().trim());
        }
        // TODO: transform contact ORCIDSs
        return Optional.of(d);
        
      } catch (IOException e) {
        LOG.error("Error reading metadata", e);
      }
    }
    return Optional.empty();
  }

}
