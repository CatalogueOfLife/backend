package life.catalogue.importer;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.csv.AcefReader;
import life.catalogue.csv.ColdpReader;
import life.catalogue.csv.DwcaReader;
import life.catalogue.importer.proxy.DistributedArchiveService;
import life.catalogue.importer.txttree.TxtTreeInserter;

import org.gbif.dwc.terms.AcefTerm;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class DataFormatDetector {
  private static final Logger LOG = LoggerFactory.getLogger(DataFormatDetector.class);
  private static final Set<String> TREE_SUFFICES = Set.of("txtree", "tree", "txt", "text");
  private static final Set<String> DATA_SUFFICES = Set.of("csv", "tsv", "tab", "txt");
  private static final Set<ColdpTerm> COLDP_CORE = Set.of(ColdpTerm.Name, ColdpTerm.Taxon, ColdpTerm.NameUsage, ColdpTerm.Reference);
  // the subfolders the csv readers look into besides the root, see CsvReader.discoverSchemas
  private static final Set<String> DATA_FOLDERS = Set.of("data", "coldp");
  private static final String ACEF_CORE = AcefTerm.AcceptedSpecies.name().toLowerCase();
  private static final String BOM = "\uFEFF";
  private static final int MAX_HEADER = 64 * 1024;

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

  /**
   * Detects the format of a source archive as the import stored it, without extracting it.
   * A zip is read through its central directory, so only the entry names and the header rows of some data files are
   * read. Other archives - the tar.gz of old ACEF sources - have no directory and are streamed once, keeping only the
   * names and the first line of data files. A single plain or gzipped file, which the import reads as a DwC core or a
   * text tree, is told by its content.
   * It follows the layout rules of the readers rather than parsing the data, so it can tell the format of old imports
   * that never recorded it: meta.xml or a data file with DwC headers is DwC-A, a ColDP core file with ColDP headers is
   * ColDP, AcceptedSpecies is ACEF and a single tree file is a text tree. metadata.yaml and eml.xml are used by several
   * formats and decide nothing.
   *
   * @param archive the stored source archive
   * @return the format or empty if the archive does not show it
   */
  public static Optional<DataFormat> detectArchive(File archive) throws IOException {
    try (ZipFile zip = new ZipFile(archive)) {
      List<Entry> entries = new ArrayList<>();
      for (ZipEntry e : Collections.list(zip.entries())) {
        if (!e.isDirectory()) {
          entries.add(new Entry(e.getName(), () -> {
            try (InputStream in = zip.getInputStream(e)) {
              return firstLine(in);
            }
          }));
        }
      }
      return detectEntries(entries);

    } catch (ZipException e) {
      // no zip, see CompressionUtil.decompressFile
      return detectStream(archive);
    }
  }

  private static Optional<DataFormat> detectStream(File archive) throws IOException {
    try (InputStream fin = new BufferedInputStream(new FileInputStream(archive))) {
      InputStream in;
      try {
        in = new BufferedInputStream(new CompressorStreamFactory().createCompressorInputStream(fin));
      } catch (CompressorException e) {
        in = fin; // not compressed
      }
      try (ArchiveInputStream<?> ain = new ArchiveStreamFactory().createArchiveInputStream(in)) {
        List<Entry> entries = new ArrayList<>();
        ArchiveEntry ae;
        while ((ae = ain.getNextEntry()) != null) {
          if (!ae.isDirectory()) {
            // a stream has no random access, so keep the first line of every data file on the way
            final List<String> header = isDataFile(ae.getName()) ? firstLine(ain) : List.of();
            entries.add(new Entry(ae.getName(), () -> header));
          }
        }
        return detectEntries(entries);

      } catch (ArchiveException e) {
        // a single file, plain or compressed. The readers take it as a DwC core or a text tree
        in.mark(1024 * 1024);
        if (isDwcHeader(firstLine(in))) {
          return Optional.of(DataFormat.DWCA);
        }
        in.reset();
        if (org.gbif.txtree.Tree.verify(in).valid) {
          return Optional.of(DataFormat.TEXT_TREE);
        }
        return Optional.empty();
      }
    }
  }

  /**
   * A file of an archive with lazy access to the column names of its header row.
   */
  private record Entry(String path, HeaderSupplier header) {}

  @FunctionalInterface
  private interface HeaderSupplier {
    List<String> get() throws IOException;
  }

  private static Optional<DataFormat> detectEntries(List<Entry> all) throws IOException {
    // visible files only, the way CompressionUtil extracts them
    List<Entry> entries = all.stream().filter(e -> !isHidden(e.path)).toList();
    // the import removes a single wrapping root folder, e.g. the one of github archives
    final String root = commonRoot(entries);
    // the files in the root folder and those in the data folders, the folders the csv readers look into
    Map<String, Entry> top = new LinkedHashMap<>();
    Map<String, Entry> data = new LinkedHashMap<>();
    for (Entry e : entries) {
      String path = e.path.substring(root.length());
      int slash = path.indexOf('/');
      if (slash < 0) {
        top.put(path, e);
        data.put(path, e);
      } else if (slash == path.lastIndexOf('/') && DATA_FOLDERS.contains(path.substring(0, slash).toLowerCase())) {
        data.put(path.substring(slash + 1), e);
      }
    }

    if (top.keySet().stream().anyMatch(DwcaReader.META_FN::equalsIgnoreCase)) {
      return Optional.of(DataFormat.DWCA);
    }
    for (var entry : data.entrySet()) {
      ColdpTerm rowType = coldpCoreFile(entry.getKey());
      if (rowType != null && isColdpHeader(entry.getValue().header.get(), rowType)) {
        return Optional.of(DataFormat.COLDP);
      }
    }
    if (data.keySet().stream().anyMatch(n -> normalize(FilenameUtils.getBaseName(n)).equals(ACEF_CORE))) {
      return Optional.of(DataFormat.ACEF);
    }
    // a DwC archive without meta.xml, read like plain csv files
    for (var entry : data.entrySet()) {
      if (isDataFile(entry.getKey()) && isDwcHeader(entry.getValue().header.get())) {
        return Optional.of(DataFormat.DWCA);
      }
    }
    // a single tree file, see TxtTreeInserter.findReadable
    long trees = top.keySet().stream()
      .filter(n -> !n.toLowerCase().startsWith("expected") && !n.toLowerCase().startsWith("readme"))
      .filter(n -> TREE_SUFFICES.contains(FilenameUtils.getExtension(n).toLowerCase()))
      .count();
    if (trees == 1) {
      return Optional.of(DataFormat.TEXT_TREE);
    }
    return Optional.empty();
  }

  private static boolean isHidden(String path) {
    for (String seg : path.split("/")) {
      if (seg.startsWith(".") || seg.equals("__MACOSX")) return true;
    }
    return false;
  }

  /**
   * @return the single folder all entries share, with its trailing slash, or an empty string
   */
  private static String commonRoot(List<Entry> entries) {
    String root = null;
    for (Entry e : entries) {
      int idx = e.path.indexOf('/');
      if (idx < 0) return "";
      String r = e.path.substring(0, idx + 1);
      if (root == null) {
        root = r;
      } else if (!root.equals(r)) {
        return "";
      }
    }
    return root == null ? "" : root;
  }

  private static String normalize(String x) {
    return x.replaceAll("[-_ ]+", "").toLowerCase();
  }

  private static boolean isDataFile(String filename) {
    return DATA_SUFFICES.contains(FilenameUtils.getExtension(filename).toLowerCase());
  }

  /**
   * @return the ColDP core row type a file is named after, also in the plural, or null. See CsvReader.detectRowType
   */
  @VisibleForTesting
  static ColdpTerm coldpCoreFile(String filename) {
    if (!isDataFile(filename)) return null;
    String base = FilenameUtils.getBaseName(filename);
    if (base.isBlank()) return null;
    ColdpTerm rt = ColdpTerm.find(base, true);
    if (rt == null && base.length() > 1 && base.toLowerCase().endsWith("s")) {
      rt = ColdpTerm.find(base.substring(0, base.length() - 1), true);
    }
    if (rt == null && base.equalsIgnoreCase("taxa")) {
      rt = ColdpTerm.Taxon;
    }
    return rt != null && COLDP_CORE.contains(rt) ? rt : null;
  }

  /**
   * Reads the first line of a stream byte by byte, so nothing beyond it is consumed, and splits it into columns
   * on the most frequent of the delimiters the csv readers detect.
   * @return the column names of the header row, without quotes and namespace prefixes
   */
  @VisibleForTesting
  static List<String> firstLine(InputStream in) throws IOException {
    var buf = new java.io.ByteArrayOutputStream();
    int b;
    // old mac files end their lines with a carriage return only
    while ((b = in.read()) >= 0 && b != '\n' && b != '\r' && buf.size() < MAX_HEADER) {
      buf.write(b);
    }
    String line = buf.toString(StandardCharsets.UTF_8).replace(BOM, "").strip();
    if (line.isEmpty()) return List.of();
    String delim = "\t";
    long max = line.chars().filter(c -> c == '\t').count();
    for (char c : new char[]{',', ';', '|'}) {
      long cnt = line.chars().filter(x -> x == c).count();
      if (cnt > max) {
        max = cnt;
        delim = String.valueOf(c);
      }
    }
    List<String> cols = new ArrayList<>();
    for (String col : line.split(java.util.regex.Pattern.quote(delim))) {
      col = col.strip().replace("\"", "");
      int colon = col.indexOf(':');
      if (colon >= 0) {
        col = col.substring(colon + 1);
      }
      if (!col.isEmpty()) {
        cols.add(col);
      }
    }
    return cols;
  }

  /**
   * More than half of the columns must be ColDP terms of the row type.
   * A DwC header is never one: a denormalised DwC taxon file shares most of its classification columns with ColDP.
   */
  @VisibleForTesting
  static boolean isColdpHeader(List<String> cols, ColdpTerm rowType) {
    if (cols.isEmpty() || isDwcHeader(cols)) {
      return false;
    }
    var terms = ColdpTerm.RESOURCES.get(rowType);
    long valid = cols.stream()
      .map(c -> ColdpTerm.find(c, false))
      .filter(t -> t != null && terms.contains(t))
      .count();
    return valid * 2 > cols.size();
  }

  /**
   * A DwC core file names its taxa with taxonID and scientificName, which no ColDP or ACEF core file has together.
   */
  @VisibleForTesting
  static boolean isDwcHeader(List<String> cols) {
    return cols.stream().anyMatch("taxonID"::equalsIgnoreCase) && cols.stream().anyMatch("scientificName"::equalsIgnoreCase);
  }

  public static boolean isProxyDescriptor(Path source) {
    try (InputStream is = new FileInputStream(source.toFile())) {
      return DistributedArchiveService.isReadable(is);
    } catch (IOException e) {
      return false;
    }
  }
}
