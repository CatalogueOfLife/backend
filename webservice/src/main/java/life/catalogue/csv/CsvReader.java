package life.catalogue.csv;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.ResultIterator;
import com.univocity.parsers.common.TextParsingException;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.common.io.PathUtils;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.importer.MappingFlags;
import life.catalogue.importer.NormalizationFailedException;
import org.apache.commons.lang3.StringUtils;
import org.gbif.dwc.terms.*;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A reader giving access to a set of delimited text files in a folder
 * by offering verbatim values as standard TermRecords.
 * <p>
 * Very basic value cleaning is done:
 * - NULL and \N values are considered null
 * - Whitespace including control characters is trimmed and collapsed to a single space
 * <p>
 * It forms the basis for reading both DWC and ACEF files.
 */
public class CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(CsvReader.class);
  protected static final CsvParserSettings CSV = new CsvParserSettings();
  public static final String LOGO_FILENAME = "logo.png";
  private static Term UNKNOWN_TERM = TermFactory.instance().findTerm("void", false);
  static {
    CSV.detectFormatAutomatically();
    // try with tabs as default if autoconfig fails
    CSV.getFormat().setDelimiter('\t');
    CSV.setSkipEmptyLines(true);
    CSV.trimValues(true);
    CSV.setReadInputOnSeparateThread(false);
    CSV.setNullValue(null);
    CSV.setMaxColumns(256);
    CSV.setMaxCharsPerColumn(1024 * 256);
  }
  
  private static final Set<String> SUFFICES = ImmutableSet.of("csv", "tsv", "tab", "txt", "text", "archive", "dwca");
  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL|null)\\s*$");
  private static final int STREAM_CHARACTERISTICS = Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.IMMUTABLE;
  
  private static final Joiner LINE_JOIN = Joiner.on('\n');
  
  protected final Path folder;
  private final String subfolder;
  protected final Map<Term, Schema> schemas = Maps.newHashMap();
  protected final MappingFlags mappingFlags = new MappingFlags();
  private static final Character[] delimiterCandidates = {'\t', ',', ';', '|'};
  // we also use \0 for hopefully no quote...
  private static final Character[] quoteCandidates = {'"', '\'', '\0'};
  
  /**
   * @param folder
   */
  protected CsvReader(Path folder, String termPrefix, String subfolder) throws IOException {
    if (!Files.isDirectory(folder)) {
      throw new FileNotFoundException("Folder does not exist: " + folder);
    }
    this.folder = folder;
    this.subfolder = subfolder;
    discoverSchemas(termPrefix);
    validate();
  }
  
  /**
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   * @throws IOException
   */
  protected void discoverSchemas(String termPrefix) throws IOException {
    // allow root directory, optional data subfolder or a subfolder called like the format
    List<Path> dirs = Lists.newArrayList(folder, folder.resolve("data"));
    if (subfolder != null) {
      dirs.add(folder.resolve(subfolder));
    }
    
    for (Path dir : dirs) {
      for (Path df : listDataFiles(dir)) {
        putSchema(buildSchema(df, termPrefix));
      }
      discoverMoreSchemas(dir);
    }
  }
  
  protected void discoverMoreSchemas(Path dir) throws IOException {
    // override to discover more schemas in any of the supported folders
  }
  
  /**
   * Override to add custom validations and set the mapping flags
   */
  protected void validate() {
    // check at least one schema exists
    if (isEmpty()) {
      throw new NormalizationFailedException.SourceInvalidException("No data files found in " + folder);
    }
  }

  /**
   * Replaces an existing schema by its row type.
   * Can be used to "modify" a schema which is final.
   */
  protected void updateSchema(Schema s) {
    schemas.put(s.rowType, s);
  }

  protected void putSchema(Schema s) {
    if (s != null) {
      if (schemas.containsKey(s.rowType)) {
        Schema first = schemas.get(s.rowType);
        if (rowTypeMatchesFilename(s) && !rowTypeMatchesFilename(first)) {
          LOG.warn("Replace existing schema for rowtype {} with new schema {} that also matches the filename", s.rowType, s);
          schemas.put(s.rowType, s);
        } else {
          LOG.warn("Rowtype {} exists already. Skip later schema {}", s.rowType, s);
        }
      } else {
        schemas.put(s.rowType, s);
      }
    }
  }

  private static boolean rowTypeMatchesFilename(Schema s) {
    String fn = PathUtils.getBasename(s.file);
    return fn.equalsIgnoreCase(s.rowType.simpleName());
  }

  protected void filterSchemas(Predicate<Term> keepRowType) {
    // allow only COL row types
    Iterator<Schema> iter = schemas.values().iterator();
    while (iter.hasNext()) {
      Schema s = iter.next();
      if (!keepRowType.test(s.rowType)) {
        LOG.info("Remove non COL rowType {} for file {}", s.rowType, s.file);
        iter.remove();
      }
    }
  }
  
  /**
   * Returns a path within the folder for a given relative file or path.
   *
   * @param filename to resolve
   */
  protected Path resolve(String filename) {
    return folder.resolve(filename);
  }
  
  /**
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   */
  public static CsvReader from(Path folder, String termPrefix) throws IOException {
    return new CsvReader(folder, termPrefix, null);
  }

  public static CsvReader from(Path folder) throws IOException {
    return from(folder, null);
  }
  
  protected void requireSchema(Term rowType) {
    if (!hasData(rowType)) {
      throw new NormalizationFailedException.SourceInvalidException(rowType + " file required but missing from " + folder);
    }
  }

  protected Term requireOneSchema(Term... rowTypes) {
    for (Term rt : rowTypes) {
      if (hasData(rt)) {
        return rt;
      }
    }
    throw new NormalizationFailedException.SourceInvalidException("One of " + concat(rowTypes) + " files required but all are missing from " + folder);
  }

  protected void require(Term rowType, Term... term) {
    for (Term t : term) {
      if (hasData(rowType) && !hasData(rowType, t)) {
        Schema s = schemas.remove(rowType);
        LOG.warn("Required term {} missing. Ignore file {}!", t, s.file);
      }
    }
  }

  /**
   * Makes sure at least one of the given terms is mapped
   */
  protected void requireOne(Term rowType, Term... terms) {
    if (hasData(rowType)) {
      for (Term t : terms) {
        if (hasData(rowType, t)) {
          // found at least one, good!
          return;
        }
      }
      Schema s = schemas.remove(rowType);
      LOG.warn("One term required from {} but all missing. Ignore file {}!", concat(terms), s.file);
    }
  }

  private static String concat(Term... terms){
    return Arrays.stream(terms).map(Term::prefixedName).collect(Collectors.joining(", "));
  }

  protected void disallow(Term rowType, Term term) {
    if (hasData(rowType) && hasData(rowType, term)) {
      LOG.warn("Removing disallowed term {} in {}", term, rowType);
      Schema s = schemas.get(rowType);
      List<Schema.Field> cols = new ArrayList<>();
      for (Schema.Field f : s.columns) {
        if (f.term == term) {
          cols.add(new Schema.Field(UNKNOWN_TERM, f.index));
        } else {
          cols.add(f);
        }
      }
      Schema s2 = new Schema(s.file, s.rowType, s.encoding, s.settings, cols);
      schemas.put(rowType, s2);
    }
  }

  protected <T extends Enum & Term> void reportMissingSchemas(Class<T> enumClass) {
    for (T t : enumClass.getEnumConstants()) {
      if (t.isClass() && !hasData(t)) {
        LOG.debug("{} schema not existing in {}", t.prefixedName(), enumClass.getSimpleName(), folder);
      }
    }
  }
  
  private static Optional<Term> findTerm(String termPrefix, String name, boolean isClassTerm) {
    // TermFactory will through IAE in case there is whitespace in the term name. Avoid that by cleaning up the provided argument first.
    name = name.replaceAll("\\s","_");
    String qualName = name;
    if (termPrefix != null && !name.contains(":")) {
      qualName = termPrefix + ":" + name;
    }
    try {
      Term t = VocabularyUtils.findTerm(qualName, isClassTerm);
      if (t instanceof UnknownTerm) {
        // avoid that the prefix is being used as part of the unknown URI
        t = UnknownTerm.build(name, isClassTerm);
        TermFactory.instance().registerTerm(t);
        return Optional.of(t);
      }
      return Optional.of(t);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
  
  public MappingFlags getMappingFlags() {
    return mappingFlags;
  }
  
  /**
   * Detects the used CSV format by trying all combinations of delimiter and quote
   * and selecting the one with the most columns in a consistent manner
   */
  @VisibleForTesting
  static CsvParserSettings discoverFormat(List<String> lines) {
    List<CsvParserSettings> candidates = Lists.newLinkedList();
    for (char del : delimiterCandidates) {
      for (char quote : quoteCandidates) {
        CsvParserSettings cfg = CSV.clone();
        cfg.getFormat().setDelimiter(del);
        cfg.setDelimiterDetectionEnabled(false);
        cfg.getFormat().setQuote(quote);
        cfg.getFormat().setQuoteEscape(quote);
        cfg.setQuoteDetectionEnabled(false);
        candidates.add(cfg);
      }
    }
    // also try univocitys autodetection if nothing works
    CsvParserSettings univoc = CSV.clone();
    univoc.setDelimiterDetectionEnabled(true);
    univoc.setQuoteDetectionEnabled(true);
    candidates.add(univoc);
    
    // find best settings, default to autodetection if all others fail
    CsvParserSettings best = univoc;
    int maxCols = 0;
    int minTotalLength = 0;
    for (CsvParserSettings cfg : candidates) {
      try {
        CsvParser parser = new CsvParser(cfg);
        int cols = 0;
        int totalLength = 0;
        for (String[] row : parser.parseAll(new StringReader(LINE_JOIN.join(lines)))) {
          if (isAllNull(row)) continue;
          
          if (cols == 0) {
            cols = row.length;
          } else if (cols != row.length) {
            // inconsistent column number, stop this one
            cols = -1;
            break;
          }
          totalLength += Arrays.stream(row).mapToInt(CsvReader::nullsafeLength).sum();
        }
        if (cols > maxCols || cols == maxCols && totalLength < minTotalLength) {
          best = cfg;
          maxCols = cols;
          minTotalLength = totalLength;
        }
        
      } catch (TextParsingException e) {
        // parser exception, e.g. if too many columns.
        // Swallow and simply consider failed attempt
      }
    }
    return best;
  }
  
  private static int nullsafeLength(String x) {
    return x == null ? 0 : x.length();
  }
  
  private Schema buildSchema(Path df, @Nullable String termPrefix) {
    LOG.debug("Detecting schema for file {}", PathUtils.getFilename(df));
    try {
      try (CharsetDetectingStream in = CharsetDetectingStream.create(Files.newInputStream(df))) {
        final Charset charset = in.getCharset();
        LOG.info("Use encoding {} for file {}", charset, PathUtils.getFilename(df));
        
        List<String> lines = Lists.newArrayList();
        BufferedReader br = CharsetDetectingStream.createReader(in, in.getCharset());
        String line;
        while ((line = br.readLine()) != null && lines.size() < 20) {
          lines.add(line);
        }
        br.close();

        if (lines.size() < 2) {
          // first line MUST be a header row...
          LOG.warn("{} contains no data", PathUtils.getFilename(df));
          
        } else {
          CsvParserSettings set = discoverFormat(lines);
          
          CsvParser parser = new CsvParser(set);
          parser.beginParsing(new StringReader(LINE_JOIN.join(lines)));
          String[] header = parser.parseNext();
          parser.stopParsing();
          
          if (header == null) {
            LOG.warn("{} contains no data", PathUtils.getFilename(df));
            
          } else {
            int idx = 0;
            List<Schema.Field> columns = Lists.newArrayList();
            int unknownCounter = 0;
            for (String col : header) {
              if (StringUtils.isBlank(col)) continue;
              Optional<Term> termOpt = findTerm(termPrefix, col, false);
              if (termOpt.isPresent()) {
                Term term = termOpt.get();
                columns.add(new Schema.Field(term, idx++));
                if (term instanceof UnknownTerm) {
                  unknownCounter++;
                  LOG.info("Unknown Term {} found in file {}", term.qualifiedName(), PathUtils.getFilename(df));
                }
              } else {
                idx++;
                LOG.warn("Illegal term {} found in file {}", col, PathUtils.getFilename(df));
              }
            }
            if (columns.isEmpty()) {
              LOG.warn("No terms found in header");
              return null;
            }
            
            int unknownPerc = unknownCounter * 100 / columns.size();
            if (unknownPerc > 80) {
              LOG.warn("{} percent unknown terms found as header", unknownPerc);
            }
            // ignore header row - needs changed if we parse the settings externally
            set.setNumberOfRowsToSkip(1);
            
            // we create a tmp dummy schema with wrong rowType for convenience to find the real rowType - it will not survive
            final Optional<Term> rowType = detectRowType(new Schema(df, DwcTerm.Taxon, charset, set, columns), termPrefix);
            if (rowType.isPresent()) {
              LOG.info("CSV {} schema with {} columns found for {} encoded file {}: {}",
                rowType.get().prefixedName(), columns.size(), charset, PathUtils.getFilename(df),
                columns.stream()
                       .map(Schema.Field::toString)
                       .collect(Collectors.joining(","))
              );
              return new Schema(df, rowType.get(), charset, set, columns);
            }
            LOG.warn("Failed to identify row type for {}", PathUtils.getFilename(df));
          }
        }
      }
      
      
    } catch (RuntimeException | IOException e) {
      LOG.error("Failed to read schema for {}", PathUtils.getFilename(df), e);
    }
    return null;
  }
  
  protected Optional<Term> detectRowType(Schema schema, String termPrefix) {
    String fn = PathUtils.getBasename(schema.file);
    // special treatment for archives which are just one CSV file - the filename does not matter in this case!
    String ext = PathUtils.getFileExtension(schema.file);
    Optional<Term> rt;
    if (ext.equalsIgnoreCase(NormalizerConfig.ARCHIVE_SUFFIX)) {
      // use dwc core
      rt = Optional.of(DwcTerm.Taxon);
      LOG.info("Use dwc:Taxon rowType for single text file {}", schema.file);
      
    } else {
      rt = findTerm(termPrefix, fn, true);
      if (!rt.isPresent() || rt.get() instanceof UnknownTerm) {
        Optional<Term> orig = rt;
        // try without plural s
        if (fn.endsWith("s")) {
          rt = findTerm(termPrefix, fn.substring(0,fn.length() - 1), true);
        }
        // specials for taxon/taxa
        if (!rt.isPresent() || rt.get() instanceof UnknownTerm) {
          if (fn.equalsIgnoreCase("taxa")) {
            rt = findTerm(termPrefix, "Taxon", true);
          }
        }
        // revert to first parsed unknown term
        if (!rt.isPresent() || rt.get() instanceof UnknownTerm) {
          rt = orig;
        }
      }
    }
    
    return rt;
  }
  
  protected static Iterable<Path> listDataFiles(Path folder) throws IOException {
    return PathUtils.listFiles(folder, SUFFICES);
  }
  
  protected static Iterable<Path> listFiles(Path folder) throws IOException {
    return PathUtils.listFiles(folder, null);
  }
  
  protected void detectMappedClassification(Term rowType, Map<Term, Rank> terms) {
    schema(rowType).ifPresent(s -> {
      for (Map.Entry<Term, Rank> ent : terms.entrySet()) {
        if (s.hasTerm(ent.getKey())) {
          mappingFlags.getDenormedRanksMapped().add(ent.getValue());
        }
      }
    });
  }

  public Optional<Path> logo() {
    List<Path> sources = Lists.newArrayList(folder);
    if (subfolder != null) {
      sources.add(folder.resolve(subfolder));
    }
    for (Path p : sources) {
      Path logo = p.resolve(LOGO_FILENAME);
      if (Files.exists(logo)) return Optional.of(logo);
    }
    return Optional.empty();
  }

  public Set<Term> rowTypes() {
    return schemas.keySet();
  }
  
  public Collection<Schema> schemas() {
    return schemas.values();
  }
  
  public boolean hasData(Term rowType) {
    return schemas.containsKey(rowType);
  }
  
  public boolean hasData(Term rowType, Term term) {
    return schemas.containsKey(rowType) && schemas.get(rowType).hasTerm(term);
  }
  
  /**
   * @return number of available schemas
   */
  public int size() {
    return schemas.size();
  }
  
  /**
   * @return true if no schema is mapped
   */
  public boolean isEmpty() {
    return schemas.isEmpty();
  }
  
  public Optional<Schema> schema(Term rowType) {
    return Optional.ofNullable(schemas.get(rowType));
  }
  
  public boolean hasSchema(Term rowType) {
    return schemas.containsKey(rowType);
  }

  public Stream<VerbatimRecord> stream(Term rowType) {
    Preconditions.checkArgument(rowType.isClass(), "RowType " + rowType + " is not a class term");
    if (schemas.containsKey(rowType)) {
      return stream(schemas.get(rowType));
    } else {
      return Stream.empty();
    }
  }
  
  /**
   * Returns the first content row of the given data file, skipping any header if existing.
   */
  public Optional<VerbatimRecord> readFirstRow(AcefTerm rowType) {
    if (schemas.containsKey(rowType)) {
      return stream(schemas.get(rowType)).findFirst();
    }
    return Optional.empty();
  }
  
  private class TermRecIterator implements Iterator<VerbatimRecord> {
    private final ResultIterator<String[], ParsingContext> iter;
    private final Schema s;
    private final int maxIdx;
    private final String filename;
    private long records;
    private long skipped;
    private boolean skippedLast;
    private String[] row;
    
    TermRecIterator(Schema schema) throws IOException {
      s = schema;
      filename = PathUtils.getFilename(schema.file);
      maxIdx = schema.columns.stream().map(f -> f.index).filter(Objects::nonNull).reduce(Integer::max).orElse(0);
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
      skippedLast = false;
      if (iter.hasNext()) {
        while (iter.hasNext() && isEmpty(row = iter.next(), true));
        // if the last rows were empty we would getUsage the last non empty row again, clear it in that case!
        if (!iter.hasNext() && isEmpty(row, false)) {
          row = null;
        } else {
          records++;
        }
      } else {
        row = null;
      }
      // log stats at the end
      if (row == null) {
        LOG.info("Read {} records from file {}, skipping {} bad lines in total", records, filename, skipped);
      }
    }
    
    private boolean isEmpty(String[] row, boolean log) {
      if (row == null) {
        // ignore this row, dont log
      } else if (row.length < maxIdx + 1) {
        if (log) {
          skippedLast = true;
          skipped++;
          LOG.info("{} skip line {} with too few columns (found {}, expected {})", filename, iter.getContext().currentLine(), row.length, maxIdx + 1);
        }
      } else if (isAllNull(row)) {
        if (log) {
          skippedLast = true;
          skipped++;
          LOG.debug("{} skip line {} with only empty columns", filename, iter.getContext().currentLine());
        }
      } else {
        return false;
      }
      return true;
    }

    @Override
    public VerbatimRecord next() {
      VerbatimRecord tr = new VerbatimRecord(iter.getContext().currentLine() - 1, filename, s.rowType);
      for (Schema.Field f : s.columns) {
        if (f != null) {
          String val = null;
          if (f.index != null) {
            if (!Strings.isNullOrEmpty(row[f.index])) {
              val = clean(row[f.index]);
            }
          }
          // use default?
          if (val == null && f.value != null) {
            val = f.value;
          }
          if (val != null) {
            tr.put(f.term, val);
          }
        }
      }
      if (skippedLast) {
        tr.addIssue(Issue.PREVIOUS_LINE_SKIPPED);
      }
      // load next non empty row
      nextRow();
      return tr;
    }
  }
  
  private Stream<VerbatimRecord> stream(final Schema s) {
    try {
      return StreamSupport.stream(
          Spliterators.spliteratorUnknownSize(new TermRecIterator(s), STREAM_CHARACTERISTICS), false);
      
    } catch (IOException | RuntimeException e) {
      LOG.error("Failed to read {}", s.file, e);
      return Stream.empty();
    }
  }
  
  private static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    return Strings.emptyToNull(CharMatcher.javaIsoControl().trimAndCollapseFrom(x, ' ').trim());
  }
  
  private static boolean isAllNull(String[] row) {
    for (String x : row) {
      if (x != null) return false;
    }
    return true;
  }
}
