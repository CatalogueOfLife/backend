package org.col.admin.task.importer.acef;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.FilenameUtils;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.dwca.VerbatimRecordFactory;
import org.col.api.model.TermRecord;
import org.col.api.vocab.VocabularyUtils;
import org.col.util.io.CharsetDetection;
import org.col.util.io.PathUtils;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 *
 */
public class AcefReader {
  private static final Logger LOG = LoggerFactory.getLogger(AcefReader.class);
  private static final Pattern EMPTY = Pattern.compile("^[, \t\r\n\f\"]*$");
  private static final Splitter TAB = Splitter.on('\t').trimResults();
  private static final Splitter CSV = Splitter.on(',').trimResults();
  private static final Map<String, Splitter> DATA_FILE_TYPES = ImmutableMap.<String, Splitter>builder()
      .put("csv",  CSV)
      .put("txt",  TAB)
      .put("tsv",  TAB)
      .put("tab",  TAB)
      .put("text", TAB)
      .build();

  private final File folder;
  private final Map<Term, Schema> schemas = Maps.newHashMap();

  private AcefReader(File folder) {
    this.folder = folder;
  }

  private static class Schema {
    final Path file;
    final Charset encoding;
    final Splitter splitter;
    final boolean quoted;
    final boolean header;
    final List<Term> columns;

    private Schema(Path file, Charset encoding, Splitter splitter, List<Term> columns) {
      this.file = file;
      this.encoding = encoding;
      this.splitter = splitter;
      this.columns = columns;
      this.quoted = false;
      this.header = true;
    }

    public boolean isEmpty() {
      return columns.isEmpty();
    }
  }

  /**
   * Scans a folder with files for ACEF files, named as ACEF class terms.
   * If present a header row will be used in each file to determine the column mapping.
   * Otherwise a standard ordering given in the
   * CoL Data Submission Format, ver. 4 of 29th September 2014 is used.
   */
  public static AcefReader from(File folder) throws IOException {
    if (!folder.exists() || !folder.isDirectory()) {
      throw new FileNotFoundException("ACEF folder does not exist: " + folder.getAbsolutePath());
    }

    AcefReader reader = new AcefReader(folder);
    for (Path df : listDataFiles(folder)) {
      Term rowType = VocabularyUtils.TF.findTerm("acef:" + PathUtils.getBasename(df));
      LOG.debug("Detecting schema for file {}, rowType={}", PathUtils.getFilename(df), rowType);
      if (rowType != null && rowType instanceof AcefTerm) {
        Schema s = buildSchema(df);
        if (s != null) {
          reader.schemas.put(rowType, s);
        }
      }
    }

    reader.validate();

    return reader;
  }

  private static Schema buildSchema(Path df) {

    Charset charset = null;
    try {
      charset = CharsetDetection.detectEncoding(df);
      if (charset.equals(Charsets.UTF_8)) {
        LOG.debug("Using encoding {} for file {}", charset, df);
      } else {
        LOG.warn("Using non standard encoding {} for file {}", charset, df);
      }

      Optional<String> header = Files.lines(df, charset).findFirst();
      if (!header.isPresent()) {
        throw new IllegalArgumentException("Header rows with ACEF terms required for " + df);

      } else {
        List<Term> columns = Lists.newArrayList();
        Splitter splitter = DATA_FILE_TYPES.getOrDefault(FilenameUtils.getExtension(df.toFile().getName()).toLowerCase(), TAB);
        for (String col : splitter.split(header.get())) {
          Term term = VocabularyUtils.TF.findTerm("acef:" + col, false);
          columns.add(term);
          if (!(term instanceof AcefTerm)) {
            LOG.warn("Non ACEF Term found: {}", term.qualifiedName());
          }
        }
        LOG.debug("{} file schema with {} column found for file {}", splitter.equals(TAB) ? "TAB" : "CSV", columns.size(), df);
        return new Schema(df, charset, splitter, columns);
      }

    } catch (RuntimeException | IOException e) {
      LOG.error("Failed to read schema for {}", df, e);
    }
    return null;
  }

  private static Iterable<Path> listDataFiles(File folder) throws IOException {
    return Files.newDirectoryStream(folder.toPath(), new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        return Files.isRegularFile(p) && DATA_FILE_TYPES.containsKey(PathUtils.getFileExtension(p));
      }
    });
  }

  private static String filename(AcefTerm rowType) {
    if (!rowType.isClass()) {
      throw new IllegalArgumentException("No class term given as row type");
    }
    return rowType.name().toLowerCase() + ".txt";
  }

  private void validate() throws NormalizationFailedException.SourceInvalidException {
    // mandatory terms.
    // Fail early, if missing ignore file alltogether!!!
    require(AcefTerm.CommonNames, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.CommonNames, AcefTerm.CommonName);
    require(AcefTerm.Distribution, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.Distribution, AcefTerm.DistributionElement);
    require(AcefTerm.Synonyms, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.Synonyms, AcefTerm.Genus);
    require(AcefTerm.Synonyms, AcefTerm.SpeciesEpithet);
    require(AcefTerm.AcceptedSpecies, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.AcceptedSpecies, AcefTerm.Genus);
    require(AcefTerm.AcceptedSpecies, AcefTerm.SpeciesEpithet);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.AcceptedTaxonID);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.ParentSpeciesID);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.InfraSpeciesEpithet);
    require(AcefTerm.AcceptedInfraSpecificTaxa, AcefTerm.InfraSpeciesMarker);

    // require at least the main accepted species file
    if (!hasData(AcefTerm.AcceptedSpecies)) {
      throw new NormalizationFailedException.SourceInvalidException(filename(AcefTerm.AcceptedSpecies) + " file required but missing from " + folder);
    }

    for (AcefTerm t : AcefTerm.values()) {
      if (t.isClass()) {
        if (!hasData(t)) {
          LOG.info("{} missing from ACEF in {}", t.name(), folder);
        }
      }
    }
  }

  private void require(Term rowType, Term term) {
    Schema s = schemas.get(rowType);
    if (s != null) {
      if (!s.columns.contains(term)) {
        LOG.warn("Required term {} missing. Ignore file {}!", term, s.file);
        schemas.remove(rowType);
      }
    }
  }

  public boolean hasData(Term rowType) {
    return schemas.containsKey(rowType);
  }

  public Stream<TermRecord> read(AcefTerm rowType) {
    Preconditions.checkArgument(rowType.isClass(), "RowType "+rowType+" is not a class term");
    if (schemas.containsKey(rowType)) {
      return read(rowType, schemas.get(rowType));
    } else {
      return Stream.empty();
    }
  }

  /**
   * Returns the first row of the given data file
   */
  public Optional<TermRecord> readFirstRow(AcefTerm rowType) {
    if (schemas.containsKey(rowType)) {
      return read(rowType, schemas.get(rowType)).findFirst();
    }
    return Optional.empty();
  }

  private Stream<TermRecord> read(final Term rowType, final Schema s) {
    final String filename = PathUtils.getFilename(s.file);
    final int cols = s.columns.size();

    Stream<String> lines;
    try {
      lines = Files.lines(s.file, s.encoding);

    } catch (IOException e) {
      LOG.error("Filed to read {}", s.file, e);
      return Stream.empty();
    }

    final LineSupplier lineSupplier = new LineSupplier(s.header);
    final ColListSupplier splitter = new ColListSupplier(s.splitter);
    return lines
        // ignore header
        .skip(s.header ? 1 : 0)
        // inc rowNum number & keep it with the row
        .map(lineSupplier)
        // skip whitespace only rows
        .filter(line -> {
          if (EMPTY.matcher(line.obj).find()) {
            LOG.debug("Skip empty row. {} line {}", filename, line.rowNum);
            return false;
          }
          return true;
        })
        .map(splitter)
        .filter(row -> {
          if (row.obj.size() < cols) {
            LOG.warn("Skip row with {} columns not {}. {} line {}", row.obj.size(), s.columns.size(), filename, row.rowNum);
            return false;
          }
          return true;
        })
        // convert into TermRecord
        .map(row -> {
          TermRecord tr = new TermRecord(row.rowNum, filename, rowType);
          for (int i = 0; i<cols; i++) {
            Term t = s.columns.get(i);
            if (t != null) {
              if (!Strings.isNullOrEmpty(row.obj.get(i))) {
                String val = VerbatimRecordFactory.clean(row.obj.get(i));
                tr.put(t, val);
              }
            }
          }
          return tr;
        });
    }

  static class ColListSupplier implements Function<LineObject<String>, LineObject<List<String>>> {
    private final Splitter splitter;

    ColListSupplier(Splitter splitter) {
      this.splitter = splitter;
    }

    @Override
    public LineObject<List<String>> apply(LineObject<String> line) {
      return new LineObject<List<String>>(line.rowNum, splitter.splitToList(line.obj));
    }
  }

  static class LineSupplier implements Function<String, LineObject<String>> {
    final AtomicInteger rowNum;

    LineSupplier(boolean header) {
      this.rowNum = new AtomicInteger(header ? 1 : 0);;
    }

    @Override
    public LineObject<String> apply(String obj) {
      return new LineObject<String>(rowNum.incrementAndGet(), obj);
    }
  }

  static class LineObject<T> {
      final int rowNum;
      final T obj;

      LineObject(int rowNum, T obj) {
        this.rowNum = rowNum;
        this.obj = obj;
      }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LineObject<?> that = (LineObject<?>) o;
      return rowNum == that.rowNum &&
          Objects.equals(obj, that.obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(rowNum, obj);
    }

    @Override
    public String toString() {
      return "#" + rowNum + ": " + obj;
    }
  }
}
