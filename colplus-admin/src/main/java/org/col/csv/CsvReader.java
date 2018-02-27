package org.col.csv;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.ResultIterator;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.col.api.model.TermRecord;
import org.col.api.vocab.VocabularyUtils;
import org.col.util.io.CharsetDetectingStream;
import org.col.util.io.PathUtils;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A reader giving access to a set of delimited text files in a folder
 * by offering verbatim values as standard TermRecords.
 *
 * It forms the basis for reading both DWC and ACEF files.
 */
public class CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(CsvReader.class);
  private static final CsvParserSettings CSV = new CsvParserSettings();
  static {
    CSV.detectFormatAutomatically();
    // try with tabs as default if autoconfig fails
    CSV.getFormat().setDelimiter('\t');
    CSV.setSkipEmptyLines(true);
    CSV.trimValues(true);
    CSV.setReadInputOnSeparateThread(false);
    CSV.setNullValue(null);
  }
  private static final Set<String> SUFFICES = ImmutableSet.of("csv", "tsv", "tab", "txt", "text");
  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL)\\s*$");

  protected final Path folder;
  protected final Map<Term, Schema> schemas;

  /**
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   * @param folder
   */
  protected CsvReader(Path folder, String termPrefix) throws IOException {
    if (!Files.isDirectory(folder)) {
      throw new FileNotFoundException("Folder does not exist: " + folder);
    }
    this.folder = folder;
    schemas = Maps.newHashMap();
    for (Path df : listDataFiles(folder)) {
      Schema s = buildSchema(df, termPrefix);
      if (s != null) {
        schemas.put(s.rowType, s);
      }
    }
  }

  /**
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   */
  public static CsvReader from(Path folder, String termPrefix) throws IOException {
    return new CsvReader(folder, termPrefix);
  }

  public static CsvReader from(Path folder) throws IOException {
    return new CsvReader(folder, null);
  }

  protected void require(Term rowType, Term term) {
    Schema s = schemas.get(rowType);
    if (s != null) {
      if (!s.columns.contains(term)) {
        LOG.warn("Required term {} missing. Ignore file {}!", term, s.file);
        schemas.remove(rowType);
      }
    }
  }

  public static class Schema {
    public final Path file;
    public final Term rowType;
    public final Charset encoding;
    public final CsvParserSettings settings;
    public final List<Term> columns;

    private Schema(Path file, Term rowType, Charset encoding, CsvParserSettings settings, List<Term> columns) {
      this.file = file;
      this.rowType = rowType;
      this.encoding = encoding;
      this.settings = settings;
      this.columns = columns;
    }

    public boolean isEmpty() {
      return columns.isEmpty();
    }
  }

  private static Term findTerm(String termPrefix, String name, boolean isClassTerm) {
    if (termPrefix != null && !name.contains(":")) {
      name = termPrefix + ":" + name;
    }
    return VocabularyUtils.TF.findTerm(name, isClassTerm);
  }

  private static Schema buildSchema(Path df, @Nullable String termPrefix) {
    final Term rowType = findTerm(termPrefix, PathUtils.getBasename(df), true);
    LOG.debug("Detecting schema for file {}, rowType={}", PathUtils.getFilename(df), rowType);

    try {
      CsvParserSettings set = CSV.clone();
      CsvParser parser = new CsvParser(set);

      try (CharsetDetectingStream in = CharsetDetectingStream.create(Files.newInputStream(df))){
        final Charset charset = in.getCharset();
        LOG.debug("Use encoding {} for file {}", charset, PathUtils.getFilename(df));

        parser.beginParsing(new InputStreamReader(in, charset));
        String[] header = parser.parseNext();
        parser.stopParsing();

        if (header == null) {
          LOG.warn("{} contains no data", PathUtils.getFilename(df));

        } else {
          List<Term> columns = Lists.newArrayList();
          int unknownCounter = 0;
          for (String col : header) {
            Term term = findTerm(termPrefix, col, false);
            columns.add(term);
            if (term instanceof UnknownTerm) {
              unknownCounter++;
              LOG.debug("Unknown Term {} found in file {}", term.qualifiedName(), PathUtils.getFilename(df));
            }
          }
          int unknownPerc = unknownCounter*100 / columns.size();
          if (unknownPerc > 80 ) {
            LOG.warn("{} percent unknown terms found as header", unknownPerc);
          }
          // ignore header row - needs changed if we parse the settings externally
          set.setNumberOfRowsToSkip(1);
          LOG.info("CSV {} schema with {} columns found for {} encoded file {}", rowType.prefixedName(), columns.size(), charset, PathUtils.getFilename(df));
          return new Schema(df, rowType, charset, set, columns);
        }
      }

    } catch (RuntimeException | IOException e) {
      LOG.error("Failed to read schema for {}", PathUtils.getFilename(df), e);
    }
    return null;
  }

  private static Iterable<Path> listDataFiles(Path folder) throws IOException {
    return Files.newDirectoryStream(folder, new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        return Files.isRegularFile(p) && SUFFICES.contains(PathUtils.getFileExtension(p));
      }
    });
  }

  public boolean hasData(Term rowType) {
    return schemas.containsKey(rowType);
  }

  public Optional<Schema> schema(Term rowType) {
    return Optional.ofNullable(schemas.get(rowType));
  }

  public Stream<TermRecord> stream(Term rowType) {
    Preconditions.checkArgument(rowType.isClass(), "RowType "+rowType+" is not a class term");
    if (schemas.containsKey(rowType)) {
      return stream(schemas.get(rowType));
    } else {
      return Stream.empty();
    }
  }

  /**
   * Returns the first content row of the given data file, skipping any header if existing.
   */
  public Optional<TermRecord> readFirstRow(AcefTerm rowType) {
    if (schemas.containsKey(rowType)) {
      return stream(schemas.get(rowType)).findFirst();
    }
    return Optional.empty();
  }

  private class TermRecIterator implements Iterator<TermRecord> {
    private final ResultIterator<String[], ParsingContext> iter;
    private final Schema s;
    private final int cols;
    private final String filename;
    private String[] row;

    TermRecIterator(Schema schema) throws IOException {
      s = schema;
      filename = PathUtils.getFilename(schema.file);
      cols = schema.columns.size();
      CsvParser parser = new CsvParser(schema.settings);

      IterableResult<String[], ParsingContext> it = parser.iterate(
          CharsetDetectingStream.createReader(Files.newInputStream(schema.file), schema.encoding)
      );
      this.iter = it.iterator();
      nextRow();
    }

    @Override
    public boolean hasNext() {
      return row != null;
    }

    private void nextRow() {
      if (iter.hasNext()) {
        while (iter.hasNext() && isEmpty(row = iter.next(), true));
        // if the last rows were empty we would get the last non empty row again, clear it in that case!
        if (!iter.hasNext() && isEmpty(row, false)) {
          row = null;
        }
      } else {
        row = null;
      }
    }

    private boolean isEmpty(String[] row, boolean log) {
      if (row == null) {
        // ignore this row, dont log
      } else if (row.length < cols) {
        if (log) LOG.info("{} skip line {} with too few columns (found {}, expected {})", filename, iter.getContext().currentLine(), row.length + 1, s.columns.size());
      } else if (isAllNull(row)) {
        if (log) LOG.debug("{} skip line {} with only empty columns", filename, iter.getContext().currentLine());
      } else {
        return false;
      }
      return true;
    }

    private boolean isAllNull(String[] row) {
      for (String x : row) {
        if (x != null) return false;
      }
      return true;
    }

    @Override
    public TermRecord next() {
      TermRecord tr = new TermRecord(iter.getContext().currentLine()-1, filename, s.rowType);
      for (int i = 0; i<cols; i++) {
        Term t = s.columns.get(i);
        if (t != null) {
          if (!Strings.isNullOrEmpty(row[i])) {
            String val = clean(row[i]);
            tr.put(t, val);
          }
        }
      }
      // load next non empty row
      nextRow();
      return tr;
    }
  }

  private Stream<TermRecord> stream(final Schema s) {
    final int character = Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.IMMUTABLE;
    try {
      return StreamSupport.stream(
          Spliterators.spliteratorUnknownSize(new TermRecIterator(s), character), false);

    } catch (IOException | RuntimeException e) {
      LOG.error("Failed to read {}", s.file, e);
      return Stream.empty();
    }
  }

  public static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    return Strings.emptyToNull(CharMatcher.javaIsoControl().trimAndCollapseFrom(x, ' ').trim());
  }

  }
