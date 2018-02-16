package org.col.admin.task.importer.acef;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.PeekingIterator;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.VerbatimRecordFactory;
import org.col.api.model.TermRecord;
import org.col.api.vocab.VocabularyUtils;
import org.col.util.CharsetDetection;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 *
 */
public class AcefReader {
  private static final Logger LOG = LoggerFactory.getLogger(AcefReader.class);
  private static final CsvMapper MAPPER = new CsvMapper()
      .enable(CsvParser.Feature.WRAP_AS_ARRAY);

  private static final CsvSchema TAB_SCHEMA = CsvSchema.emptySchema()
      .withQuoteChar('"')
      .withColumnSeparator('\t')
      .withHeader();
  private static final CsvSchema CSV_SCHEMA = CsvSchema.emptySchema()
      .withQuoteChar('"')
      .withColumnSeparator(',')
      .withHeader();


  private static final Map<String, CsvSchema> DATA_FILE_TYPES = ImmutableMap.<String, CsvSchema>builder()
      .put("csv", CSV_SCHEMA)
      .put("txt", TAB_SCHEMA)
      .put("tsv", TAB_SCHEMA)
      .put("tab", TAB_SCHEMA)
      .put("text", TAB_SCHEMA)
      .build();

  private final File folder;
  private final Map<Term, Schema> schemas = Maps.newHashMap();

  private AcefReader(File folder) {
    this.folder = folder;
  }

  private static class Schema {
    final File file;
    final CsvSchema schema;
    final Map<String, Term> columns;

    private Schema(File file, CsvSchema schema, Map<String, Term> columns) {
      this.file = file;
      this.schema = schema;
      this.columns = columns;
    }

    public boolean isEmpty() {
      return schema.size() == 0;
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
    for (File df : listDataFiles(folder)) {
      Term rowType = VocabularyUtils.TF.findTerm(AcefTerm.PREFIX + ":" + FilenameUtils.getBaseName(df.getName()));
      LOG.debug("Detecting schema for file {}, rowType={}", df.getName(), rowType);
      if (rowType != null && rowType instanceof AcefTerm) {
        reader.schemas.put(rowType, buildSchema(df));
      }
    }

    reader.validate();

    return reader;
  }

  private static MappingIterator<Map<String,String>> jacksonIter(CsvSchema schema, File df) throws IOException {
    InputStreamReader reader = null;
    Charset charset = CharsetDetection.detectEncoding(df);
    LOG.debug("Using character encoding {} for file {}", charset, df.getName());
    reader = new InputStreamReader(new FileInputStream(df), charset);
    return MAPPER.readerFor(Map.class)
        .with(schema)
        .readValues(reader);
  }

  private static Schema buildSchema(File df) throws IOException {
    Map<String,Term> columns = Maps.newHashMap();
    CsvSchema schema = DATA_FILE_TYPES.getOrDefault(FilenameUtils.getExtension(df.getName()).toLowerCase(), TAB_SCHEMA);

    MappingIterator<Map<String,String>> it = null;
    try {
      it = jacksonIter(schema, df);
      CsvSchema.Builder builder = schema.rebuild();
      if (it.hasNext()) {
        Map<String,String> rowAsMap = it.next();
        // access by column name, as defined in the header row...
        for (String name : rowAsMap.keySet()) {
          builder.addColumn(name);
          columns.put(name, VocabularyUtils.TF.findTerm("acef:" + name));
        }
        schema = builder.build();
        LOG.debug("Csv schema with {} column found for file {}", rowAsMap.size(), df.getName());
      }

    } catch (RuntimeException | IOException e) {
      LOG.error("Failed to read schema for {}", df.getName(), e);

    } finally {
      if (it != null) {
        it.close();
      }
    }

    return new Schema(df, schema, columns);
  }

  private static List<File> listDataFiles(File folder) {
    List<File> dataFiles = new ArrayList<>();
    for (String suffix : DATA_FILE_TYPES.keySet()) {
      FileFilter ff = FileFilterUtils.and(
          FileFilterUtils.suffixFileFilter("."+suffix, IOCase.INSENSITIVE), HiddenFileFilter.VISIBLE
      );
      dataFiles.addAll(Arrays.asList(folder.listFiles(ff)));
    }
    return dataFiles;
  }

  private static String filename(AcefTerm rowType) {
    if (!rowType.isClass()) {
      throw new IllegalArgumentException("No class term given as row type");
    }
    return rowType.name().toLowerCase() + ".txt";
  }

  public void validate() throws NormalizationFailedException.SourceInvalidException {
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

  public boolean hasData(Term rowType) {
    return schemas.containsKey(rowType);
  }

  public Iterable<TermRecord> read(Term rowType) {
    Preconditions.checkArgument(rowType.isClass(), "RowType "+rowType+" is not a class term");
    if (schemas.containsKey(rowType)) {
      return new TermRecIterable(schemas.get(rowType));
    } else {
      return Collections.EMPTY_LIST;
    }
  }

  /**
   * Returns the first row of the given data file
   */
  public Optional<TermRecord> readFirstRow(Term rowType) {
    if (schemas.containsKey(rowType)) {
      TermRecIterator iter = new TermRecIterator(schemas.get(rowType));
      if (iter.hasNext()) {
        return Optional.of(iter.next());
      }
    }
    return Optional.empty();
  }

  private static class TermRecIterable implements Iterable<TermRecord> {
    private final Schema schema;

    private TermRecIterable(Schema schema) {
      this.schema = schema;
    }

    @NotNull
    @Override
    public Iterator<TermRecord> iterator() {
      return new NonEmptyTermRecIterator(schema);
    }
  }

  private static class NonEmptyTermRecIterator implements Iterator<TermRecord> {
    private final PeekingIterator<TermRecord> it;

    private NonEmptyTermRecIterator(Schema schema) {
      it = Iterators.peekingIterator(new TermRecIterator(schema));
      skipEmptyRows();
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    private void skipEmptyRows() {
      while (it.hasNext() && it.peek().isEmpty()) {
        it.next();
      }
    }

    @Override
    public TermRecord next() {
      TermRecord tr = it.next();
      skipEmptyRows();
      return tr;
    }
  }

  private static class TermRecIterator implements Iterator<TermRecord> {
    private Iterator<Map<String,String>> it;
    private Schema schema;

    private TermRecIterator(Schema schema) {
      this.schema = schema;
      try {
        this.it = jacksonIter(schema.schema, schema.file);
      } catch (IOException e) {
        LOG.error("Failed to open {}", schema.file, e);
        it = Collections.emptyIterator();
      }
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public TermRecord next() {
      Map<String,String> row = it.next();
      TermRecord tr = new TermRecord();
      for (Map.Entry<String, String> col : row.entrySet()) {
        Term t = schema.columns.get(col.getKey());
        String val = VerbatimRecordFactory.clean(col.getValue());
        if (t != null && !Strings.isNullOrEmpty(val)) {
          tr.put(t, val);
        }
      }
      return tr;
    }
  }

}
