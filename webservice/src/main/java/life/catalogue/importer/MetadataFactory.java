package life.catalogue.importer;

import com.google.common.collect.ImmutableList;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.exporter.DatasetYamlWriter;
import life.catalogue.importer.coldp.ColdpMetadataParser;
import life.catalogue.importer.dwca.EmlParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A delegating metadata factory for all supported metadata formats in COL CLB.
 * Prefers yaml over json over eml.
 */
public class MetadataFactory {
  private static final Logger LOG = LoggerFactory.getLogger(MetadataFactory.class);
  private static final List<String> METADATA_YAML_FILENAMES = ImmutableList.of("metadata.yaml", "metadata.yml");
  private static final List<String> METADATA_JSON_FILENAMES = ImmutableList.of("metadata.json");
  private static final List<String> EML_FILENAMES = ImmutableList.of("eml.xml", "metadata.xml");


  /**
   * Reads any kind of metadata ChecklistBank understands, with preference on ColDP if multiple formats are available.
   * In case of parsing errors an empty optional is returned.
   */
  public static Optional<DatasetWithSettings> readMetadata(Path dir) {
    for (String fn : METADATA_YAML_FILENAMES) {
      Path metapath = dir.resolve(fn);
      if (Files.exists(metapath)) {
        try {
          return ColdpMetadataParser.readYAML(Files.newInputStream(metapath));
        } catch (Exception e) {
          LOG.error("Error reading metadata from " + fn, e);
        }
      }
    }
    // try COL API json
    for (String fn : METADATA_JSON_FILENAMES) {
      Path metapath = dir.resolve(fn);
      if (Files.exists(metapath)) {
        try {
          return ColdpMetadataParser.readJSON(Files.newInputStream(metapath));
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

}
