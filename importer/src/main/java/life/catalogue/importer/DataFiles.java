package life.catalogue.importer;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.csv.AcefReader;
import life.catalogue.csv.ColdpReader;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.DwcaReader;
import life.catalogue.importer.txttree.TxtTreeInserter;

import org.gbif.dwc.terms.AcefTerm;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lists the data files of an unpacked source folder, i.e. everything the importer would actually read as data.
 * <p>
 * Metadata is excluded on purpose: it is rewritten by many exporters on every run, so including it makes every
 * source look changed. Files that alter how the data is interpreted - the DwC-A {@code meta.xml} and the ColDP
 * {@code default.yaml} - do count as data. Anything the reader did not recognise is not listed at all.
 */
public class DataFiles {
  private static final String BIBTEX_FN = "reference.bib";
  // ColDP allows its data in the root, a data or a coldp subfolder
  private static final List<String> COLDP_DIRS = List.of("", "data", "coldp");

  private DataFiles() {}

  /**
   * @param folder the unpacked source folder
   * @param format the detected format of that folder
   * @return the data files, sorted by their path relative to the folder
   */
  public static List<Path> list(Path folder, DataFormat format) throws IOException {
    Set<Path> files = new LinkedHashSet<>();
    switch (format) {
      case COLDP -> coldp(folder, files);
      case DWCA -> dwca(folder, files);
      case ACEF -> acef(folder, files);
      case TEXT_TREE -> txtree(folder, files);
      default -> throw new IllegalArgumentException("Cannot list data files of a " + format + " source");
    }

    var sorted = new ArrayList<>(files);
    final Path base = folder.toAbsolutePath().normalize();
    sorted.sort(Comparator.comparing(p -> base.relativize(p.toAbsolutePath().normalize()).toString()));
    return sorted;
  }

  private static void coldp(Path folder, Set<Path> files) throws IOException {
    try (ColdpReader reader = ColdpReader.from(folder)) {
      schemas(reader, files);
      add(files, reader.getBibtexFile());
      add(files, reader.getCslJsonFile());
      add(files, reader.getCslJsonLinesFile());
      if (reader.hasTreatments()) {
        try (Stream<Path> treatments = reader.getTreatments()) {
          treatments.forEach(files::add);
        }
      }
    }
    // default values change how every row is interpreted, so they are data
    for (String dir : COLDP_DIRS) {
      add(files, folder.resolve(dir).resolve(ColdpReader.DEFAULT_FN));
    }
  }

  private static void dwca(Path folder, Set<Path> files) throws IOException {
    try (DwcaReader reader = DwcaReader.from(folder)) {
      schemas(reader, files);
    }
    // the descriptor maps columns to terms, so it changes the data even when no row did
    add(files, folder.resolve(DwcaReader.META_FN));
  }

  private static void acef(Path folder, Set<Path> files) throws IOException {
    try (AcefReader reader = AcefReader.from(folder)) {
      // ACEF keeps its metadata in a data row, not a separate file - exclude it like any other metadata
      reader.schemas().stream()
            .filter(s -> s.rowType != AcefTerm.SourceDatabase)
            .forEach(s -> files.addAll(s.files));
    }
  }

  private static void txtree(Path folder, Set<Path> files) {
    TxtTreeInserter.findReadable(folder).ifPresent(files::add);
    add(files, folder.resolve(BIBTEX_FN));
  }

  private static void schemas(CsvReader reader, Set<Path> files) {
    reader.schemas().forEach(s -> files.addAll(s.files));
  }

  private static void add(Set<Path> files, @Nullable File f) {
    if (f != null) {
      add(files, f.toPath());
    }
  }

  private static void add(Set<Path> files, Path p) {
    if (p != null && Files.isRegularFile(p)) {
      files.add(p);
    }
  }
}
