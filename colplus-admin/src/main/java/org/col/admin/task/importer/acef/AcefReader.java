package org.col.admin.task.importer.acef;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.col.admin.task.importer.VerbatimRecordFactory;
import org.col.api.model.TermRecord;
import org.col.api.vocab.AcefTerm;
import org.col.api.vocab.VocabularyUtils;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
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

  private final Map<Term, Schema> schemas = Maps.newHashMap();

  private static final Map<String, CsvSchema> DATA_FILE_TYPES = ImmutableMap.<String, CsvSchema>builder()
      .put("csv", CSV_SCHEMA)
      .put("txt", TAB_SCHEMA)
      .put("tsv", TAB_SCHEMA)
      .put("tab", TAB_SCHEMA)
      .put("text", TAB_SCHEMA)
      .build();


  private static class Schema {
    final File file;
    final CsvSchema schema;
    final Map<String, Term> columns;

    private Schema(File file, CsvSchema schema, Map<String, Term> columns) {
      this.file = file;
      this.schema = schema;
      this.columns = columns;
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

    AcefReader reader = new AcefReader();
    for (File df : listDataFiles(folder)) {
      Term rowType = VocabularyUtils.TF.findTerm(AcefTerm.PREFIX + ":" + FilenameUtils.getBaseName(df.getName()));
      LOG.debug("Detecting schema for file {}, rowType={}", df.getName(), rowType);
      if (rowType != null && rowType instanceof AcefTerm) {
        Schema schema = buildSchema(df);
        reader.schemas.put(rowType, schema);
      }
    }
    return reader;
  }

  private static Schema buildSchema(File df) throws IOException {
    CsvSchema baseSchema = DATA_FILE_TYPES.getOrDefault(FilenameUtils.getExtension(df.getName()).toLowerCase(), TAB_SCHEMA);
    MappingIterator<Map<String,String>> it = MAPPER.readerFor(Map.class)
        .with(baseSchema)
        .readValues(df);
    CsvSchema.Builder builder = baseSchema.rebuild();
    Map<String,Term> columns = Maps.newHashMap();
    if (it.hasNext()) {
      Map<String,String> rowAsMap = it.next();
      // access by column name, as defined in the header row...
      for (String name : rowAsMap.keySet()) {
        builder.addColumn(name);
        columns.put(name, VocabularyUtils.TF.findTerm("acef:" + name));
      }
      LOG.debug("Csv schema with {} column found for file {}", rowAsMap.size(), df.getName());
    }
    it.close();
    return new Schema(df, builder.build(), columns);
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

  public Iterator<TermRecord> iterator(Term rowType) throws IOException {
    if (schemas.containsKey(rowType)) {
      return new TermRecIterator(schemas.get(rowType));
    } else {
      return Collections.emptyIterator();
    }
  }

  static class TermRecIterator implements Iterator<TermRecord> {
    private MappingIterator<Map<String,String>> it;
    private Schema schema;

    public TermRecIterator(Schema schema) throws IOException {
      this.schema = schema;
      this.it = MAPPER.readerFor(Map.class)
          .with(schema.schema)
          .readValues(schema.file);
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
