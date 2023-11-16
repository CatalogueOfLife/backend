package life.catalogue.importer;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.csv.AcefReader;
import life.catalogue.csv.ColdpReader;
import life.catalogue.csv.DwcaReader;
import life.catalogue.importer.proxy.DistributedArchiveService;
import life.catalogue.importer.txttree.TxtTreeInserter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataFormatDetector {
  private static final Logger LOG = LoggerFactory.getLogger(DataFormatDetector.class);

  public static DataFormat detectFormat(Path folder) {
    if (!Files.isDirectory(folder)) {
      throw new IllegalArgumentException("Not a directory: " + folder);
    }

    // shortcut to detect dwca - meta.xml
    if (Files.isRegularFile(folder.resolve(DwcaReader.META_FN))) {
      return DataFormat.DWCA;
    }
    // shortcut to detect coldp - default.yaml
    if (Files.isRegularFile(folder.resolve(ColdpReader.DEFAULT_FN))) {
      return DataFormat.COLDP;
    }

    LOG.debug("Try TextTree format");
    if (TxtTreeInserter.findReadable(folder).isPresent()) {
      return DataFormat.TEXT_TREE;
    }

    try {
      LOG.debug("Try ColDP format");
      ColdpReader.from(folder);
      return DataFormat.COLDP;
    } catch (Exception e) {
      // swallow
    }

    try {
      LOG.debug("Try ACEF format");
      AcefReader.from(folder);
      return DataFormat.ACEF;
    } catch (Exception e) {
      // swallow
    }

    try {
      LOG.debug("Try DwC format");
      DwcaReader.from(folder);
      return DataFormat.DWCA;
    } catch (Exception e) {
      // swallow
    }

    LOG.debug("Try proxy format");
    if (DistributedArchiveService.isReadable(folder).isPresent()) {
      return DataFormat.PROXY;
    }

    throw new IllegalArgumentException("Unknown format in " + folder);
  }

  public static boolean isProxyDescriptor(Path source) {
    try {
      return DistributedArchiveService.isReadable(new FileInputStream(source.toFile()));
    } catch (IOException e) {
      return false;
    }
  }
}
