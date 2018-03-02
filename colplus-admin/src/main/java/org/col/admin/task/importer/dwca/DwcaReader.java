package org.col.admin.task.importer.dwca;

import com.google.common.base.Joiner;
import com.google.common.collect.*;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.lang3.StringEscapeUtils;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.api.model.TermRecord;
import org.col.api.vocab.VocabularyUtils;
import org.col.csv.CsvReader;
import org.col.util.io.CharsetDetectingStream;
import org.col.util.io.PathUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 *
 */
public class DwcaReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaReader.class);
  private static final String DWCA_NS = "http://rs.tdwg.org/dwc/text/";
  public static final Term DWCA_ID = UnknownTerm.build(DWCA_NS+"ID", "ID", false);
  private static final String META_FN = "meta.xml";
  private static final List<Term> PREFERRED_CORE_TYPES = ImmutableList.of(DwcTerm.Taxon, DwcTerm.Event, DwcTerm.Occurrence);
  private static final XMLInputFactory2 factory;
  static {
    factory = (XMLInputFactory2) XMLInputFactory2.newFactory();
    factory.configureForConvenience();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
  }
  private static final Map<Term, Term> ROW_TYPE_TO_ID = ImmutableMap.<Term, Term>builder()
      .put(DwcTerm.Occurrence, DwcTerm.occurrenceID)
      .put(DwcTerm.Event, DwcTerm.eventID)
      .put(DwcTerm.Taxon, DwcTerm.taxonID)
      .put(DwcTerm.MeasurementOrFact, DwcTerm.measurementID)
      .build();
  private Term coreRowType;
  private Map<Term, List<Field>> defaultValues;
  private Map<Term, Map<Term, Term>> aliases;
  private Path metadataFile;

  private static class Field {
    final Term term;
    final String value;
    final Integer index;
    final String delimiter;

    public Field(Term term, String value, Integer index, String delimiter) {
      this.term = term;
      this.value = value;
      this.index = index;
      this.delimiter = delimiter;
    }
  }

  private DwcaReader(Path folder) throws IOException {
    super(folder, "dwc");
    validate();
  }

  public static DwcaReader from(Path folder) throws IOException {
    return new DwcaReader(folder);
  }

  public Schema coreSchema() {
    return schemas.get(coreRowType);
  }

  public Term coreRowType() {
    return coreRowType;
  }

  public Path getMetadataFile() {
    return metadataFile;
  }

  /**
   * First tries to find and read a meta.xml file.
   * If none is found all potential txt files are scanned.
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   * @throws IOException
   */
  @Override
  protected void discoverSchemas(String termPrefix) throws IOException {
    defaultValues = Maps.newHashMap();
    aliases = Maps.newHashMap();
    Path meta = resolve(META_FN);
    if (Files.exists(meta)) {
      readFromMeta(meta);

    } else {
      super.discoverSchemas(termPrefix);

      // select core
      if (size() == 1) {
        coreRowType = schemas.keySet().iterator().next();
      } else {
        for (Term t : PREFERRED_CORE_TYPES) {
          if (hasData(t)) {
            coreRowType = t;
            LOG.warn("{} data files found but no archive descriptor. Using {}", size(), coreRowType);
            break;
          }
        }
        if (coreRowType == null) {
          // rather abort instead of picking randomly
          throw new NormalizationFailedException.SourceInvalidException("Multiple unknown schemas found: " + Joiner.on(", ").join(schemas.keySet()));
        }
      }
    }
  }

  @Override
  protected Term detectRowType(Path df, String termPrefix, List<Term> columns) {
    // we only end up here when there is no meta descriptor
    Term rowType = super.detectRowType(df, termPrefix, columns);
    if (rowType instanceof UnknownTerm) {
      // look at columns
      for (Map.Entry<Term, Term> e : ROW_TYPE_TO_ID.entrySet()) {
        if (columns.contains(e.getValue())) {
          return e.getKey();
        }
      }
    }
    return rowType;
  }

  private void buildSchema(XMLStreamReader2 parser, boolean core) throws XMLStreamException, IOException {
    // rowType
    final Term rowType = VocabularyUtils.TF.findClassTerm(attr(parser, "rowType"));
    if (core) {
      coreRowType = rowType;
    }

    // encoding
    String enc = attr(parser, "encoding");

    // delimiter
    CsvParserSettings set = CSV.clone();

    String val = unescapeBackslash(attr(parser, "fieldsTerminatedBy"));
    set.setDelimiterDetectionEnabled(true);
    if (val != null) {
      if (val.length() != 1) {
        throw new IllegalArgumentException("fieldsTerminatedBy needs to be a single char");
      } else {
        set.setDelimiterDetectionEnabled(false);
        set.getFormat().setDelimiter(val.charAt(0));
      }
    }
    val = unescapeBackslash(attr(parser, "fieldsEnclosedBy"));
    set.setQuoteDetectionEnabled(false);
    if (val != null) {
      if (val.length() != 1) {
        throw new IllegalArgumentException("fieldsEnclosedBy needs to be a single char");
      } else {
        set.getFormat().setDelimiter(val.charAt(0));
      }
    }
    // we ignore linesTerminatedBy
    // Its quite often wrong and people dont really use anything else than \n \r!
    set.setLineSeparatorDetectionEnabled(true);

    //setAttrIfExists(parser, "linesTerminatedBy", set.getFormat()::setLineSeparator);
    val = attr(parser, "ignoreHeaderLines");
    if (val != null) {
      try {
        set.setNumberOfRowsToSkip(Long.parseLong(val));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("ignoreHeaderLines needs to be a valid integer");
      }
    }

    // parse fields & file
    Path file = resolve(attr(parser, "encoding"));
    List<Field> fields = Lists.newArrayList();
    int event;
    boolean stop = false;
    StringBuilder text = new StringBuilder();
    while (!stop) {
      event = parser.next();
      stop = event == XMLStreamConstants.END_DOCUMENT;
      switch (event) {
        case XMLStreamConstants.START_ELEMENT:
          text = new StringBuilder();
          boolean id = false;
          switch (parser.getLocalName()) {
            case "id":
            case "coreid":
              id = true;
            case "field":
              fields.add(buildField(parser, id));
              break;
          }
          break;
        case XMLStreamConstants.END_ELEMENT:
          switch (parser.getLocalName()) {
            case "location":
              file = resolve(text.toString());
              break;
            case "core":
            case "extension":
              stop = true;
              break;
          }
          break;
        case XMLStreamConstants.CHARACTERS:
          if (parser.hasText()) {
            text.append(parser.getText().trim());
          }
          break;
      }
    }
    // convert fields to columns list

    Optional<Integer> maxColIdx = fields.stream().map(f->f.index).filter(Objects::nonNull).reduce(Integer::max);
    Term[] columns;
    if (maxColIdx.isPresent()) {
      if (maxColIdx.get() > 100) {
        LOG.warn("Suspicously high max column index found: {}", maxColIdx.get());
      }
      columns = new Term[maxColIdx.get()+1];
      for (Field f : fields) {
        if (f.index != null) {
          if (f.index < 0) {
            LOG.warn("Ignoring illegal negative column index for term {}", f.term);
          } else {
            if (columns[f.index] != null) {
              // we have multiple terms mapped to the same column. Remember alias terms
              if (!aliases.containsKey(rowType)) {
                aliases.put(rowType, Maps.newHashMap());
              }
              aliases.get(rowType).put(f.term, columns[f.index]);
            } else {
              columns[f.index] = f.term;
            }
          }
        }
        // defaults
        addDefault(rowType, f);
      }
    } else {
      columns = new Term[0];
      LOG.warn("No columns mapped from file for rowType {}", rowType);
    }

    // final encoding
    Charset charset;
    try {
      charset = Charset.forName(enc);
    } catch (IllegalArgumentException e) {
      try (CharsetDetectingStream in = CharsetDetectingStream.create(Files.newInputStream(file))){
        charset = in.getCharset();
        LOG.debug("Use encoding {} for file {}", charset, PathUtils.getFilename(file));
      }
      LOG.warn("Bad charset encoding {} specified, using {}", enc, charset);
    }

    Schema s = new Schema(file, rowType, charset, set, Lists.newArrayList(columns));
    LOG.debug("Found schema {}", s);
    schemas.put(rowType, s);
  }

  private void addDefault(Term rowType, Field f) {
    if (f.value != null){
      if (!defaultValues.containsKey(rowType)) {
        defaultValues.put(rowType, Lists.newArrayList());
      }
      defaultValues.get(rowType).add(f);
    }
  }

  private Field buildField(XMLStreamReader2 parser, boolean id) {
    final Term term = id ? DWCA_ID : VocabularyUtils.TF.findPropertyTerm(attr(parser, "term"));
    String value = attr(parser, "default");
    // let bad errors be thrown up
    Integer index = null;
    String indexAsString = attr(parser, "index");
    if (indexAsString != null) {
      index = Integer.parseInt(indexAsString);
    }
    String delimiter = attr(parser, "delimitedBy");
    return new Field(term, value, index, delimiter);
  }

  private static String unescapeBackslash(String x) {
    if (x == null || x.length() == 0) {
      return null;
    }
    return StringEscapeUtils.unescapeJava(x);
  }

  private static String attr(XMLStreamReader2 parser, String attrName) {
    return parser.getAttributeValue(null, attrName);
  }

  private void readFromMeta(Path meta) {
    try (CharsetDetectingStream stream = CharsetDetectingStream.create(Files.newInputStream(meta))){
      XMLStreamReader2 parser = (XMLStreamReader2) factory.createXMLStreamReader(stream, stream.getCharset().name());

      int event;
      while ((event = parser.next()) != XMLStreamConstants.END_DOCUMENT) {
        switch (event) {
          case XMLStreamConstants.START_ELEMENT:
            switch (parser.getLocalName()) {
              case "archive":
                String fn = attr(parser, "metadata");
                if (fn != null) {
                  metadataFile = resolve(fn);
                }
                break;
              case "core":
                buildSchema(parser, true);
                break;
              case "extension":
                buildSchema(parser, false);
                break;
            }
        }
      }
      parser.close();

    } catch (XMLStreamException | IOException e) {
      LOG.error("Failed to parse DwC-A descriptor: {}", e.getMessage(), e);
      throw new NormalizationFailedException.SourceInvalidException("Failed to parse DwC-A descriptor");
    }
    LOG.info("Read DwC-A descriptor with {} core and {} extensions", coreRowType, size()-1);
  }

  /**
   * Override to add dwca default values for missing values
   */
  @Override
  public Stream<TermRecord> stream(Term rowType) {
    final List<Field> defaults = ImmutableList.copyOf(defaultValues.getOrDefault(rowType, Collections.emptyList()));
    final Optional<Map<Term, Term>> alias = Optional.ofNullable(aliases.getOrDefault(rowType, null));
    final Optional<Term> idTerm = Optional.ofNullable(ROW_TYPE_TO_ID.getOrDefault(rowType, null));
    if (!defaults.isEmpty() || idTerm.isPresent() || alias.isPresent()) {
      return super.stream(rowType)
          .map(row -> {
            for (Field df : defaults) {
              if (!row.hasTerm(df.term)) {
                row.put(df.term, df.value);
              }
            }
            // include dwca id term
            idTerm.ifPresent(term -> row.put(DWCA_ID, row.get(term)));
            // include optional aliases (same column mapped to several terms)
            alias.ifPresent(aliasMap -> {
              for (Map.Entry<Term, Term> ali : aliasMap.entrySet()) {
                if (row.hasTerm(ali.getKey())) {
                  row.put(ali.getValue(), row.get(ali.getKey()));
                }
              }
            });
            return row;
          });

    } else {
      // no defaults and no id column, return original stream
      return super.stream(rowType);
    }
  }

  private void validate() throws NormalizationFailedException.SourceInvalidException {
    // no checks
    if (isEmpty()) {
      throw new NormalizationFailedException.SourceInvalidException("No data files found in " + folder);
    }
    if (coreRowType == null) {
      throw new IllegalStateException("No core rowType set");
    }
  }

}
