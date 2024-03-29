package life.catalogue.metadata;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.metadata.coldp.ColdpMetadataParser;
import life.catalogue.metadata.eml.EmlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * A delegating metadata factory for all supported metadata formats in COL CLB.
 * Prefers yaml over json over eml.
 */
public class MetadataFactory {
  private static final Logger LOG = LoggerFactory.getLogger(MetadataFactory.class);
  private static final List<String> METADATA_YAML_FILENAMES = ImmutableList.of("metadata.yaml", "metadata.yml");
  private static final List<String> METADATA_JSON_FILENAMES = ImmutableList.of("metadata.json");
  private static final List<String> EML_FILENAMES = ImmutableList.of("eml.xml", "metadata.xml");

  public static String stripHtml(String x) {
    return x == null ? null : Jsoup.parse(x).wholeText().trim();
  }

  /**
   * Reads any kind of metadata ChecklistBank understands, with preference on ColDP if multiple formats are available.
   * In case of parsing errors an empty optional is returned.
   * @param dir the directory to look for suitable metadata files
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
