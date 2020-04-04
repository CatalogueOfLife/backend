package life.catalogue.importer;

import life.catalogue.api.txtree.Tree;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.PathUtils;
import life.catalogue.csv.CsvReader;
import life.catalogue.importer.acef.AcefReader;
import life.catalogue.importer.coldp.ColdpReader;
import life.catalogue.importer.dwca.DwcaReader;
import life.catalogue.importer.proxy.ArchiveDescriptor;
import life.catalogue.importer.proxy.DistributedArchiveService;
import life.catalogue.importer.txttree.TxtTreeInserter;

import javax.xml.crypto.Data;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class DataFormatDetector {

  public static DataFormat detectFormat(Path folder) {
    if (!Files.isDirectory(folder)) {
      throw new IllegalArgumentException("Not a directory: " + folder);
    }

    if (TxtTreeInserter.findReadable(folder).isPresent()) {
      return DataFormat.TEXT_TREE;
    }

    try {
      CsvReader reader = ColdpReader.from(folder);
      return DataFormat.COLDP;
    } catch (Exception e) {
      // swallow
    }

    try {
      CsvReader reader = AcefReader.from(folder);
      return DataFormat.ACEF;
    } catch (Exception e) {
      // swallow
    }

    try {
      CsvReader reader = DwcaReader.from(folder);
      return DataFormat.DWCA;
    } catch (Exception e) {
      // swallow
    }

    if (DistributedArchiveService.isReadable(folder).isPresent()) {
      return DataFormat.PROXY;
    }

    throw new IllegalArgumentException("Unknown format in " + folder);
  }

  public static boolean isProxyDescriptor(File source) {
    try {
      return DistributedArchiveService.isReadable(new FileInputStream(source));
    } catch (IOException e) {
      return false;
    }
  }
}
