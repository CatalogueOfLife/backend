package org.col.admin.task.importer.dwca;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.text.StringEscapeUtils;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.api.model.TermRecord;
import org.col.api.vocab.VocabularyUtils;
import org.col.csv.CsvReader;
import org.col.csv.Schema;
import org.col.common.io.CharsetDetectingStream;
import org.col.common.io.PathUtils;
import org.gbif.dwc.terms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private Path metadataFile;

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

  public Optional<Path> getMetadataFile() {
    return Optional.ofNullable(metadataFile);
  }

  /**
   * First tries to find and read a meta.xml file.
   * If none is found all potential txt files are scanned.
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   * @throws IOException
   */
  @Override
  protected void discoverSchemas(String termPrefix) throws IOException {
    Path meta = resolve(META_FN);
    if (Files.exists(meta)) {
      readFromMeta(meta);

    } else {
      super.discoverSchemas(termPrefix);

      // add artificial id terms for known rowType id pairs
      for (Schema s : schemas.values()) {
        if (!s.hasTerm(DWCA_ID)) {
          Optional<Term> idTerm = Optional.ofNullable(ROW_TYPE_TO_ID.getOrDefault(s.rowType, null));
          if (idTerm.isPresent() && s.hasTerm(idTerm.get())) {
            // create another id field with the same index
            Schema.Field id = new Schema.Field(DWCA_ID, s.field(idTerm.get()).index);
            List<Schema.Field> columns = Lists.newArrayList(s.columns);
            columns.add(id);
            Schema s2 = new Schema(s.file, s.rowType, s.encoding, s.settings, columns);
            putSchema(s2);
          }
        }
      }


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
    CsvFormat format = coreSchema().settings.getFormat();
    LOG.info("Found {} core [delim={} quote={}] and {} extensions",
        coreRowType, format.getDelimiter(), format.getQuote(), size()-1);
  }

  @Override
  protected Optional<Term> detectRowType(Schema schema, String termPrefix) {
    // we only end up here when there is no meta descriptor
    // the default impl derives the rowType from the file name - not very trustworthy
    Optional<Term> rowTypeFile = super.detectRowType(schema, termPrefix);
    // so we check columns for exisiting id terms first
    Optional<Term> rowTypeCol = Optional.empty();
    for (Map.Entry<Term, Term> e : ROW_TYPE_TO_ID.entrySet()) {
      if (schema.hasTerm(e.getValue())) {
        rowTypeCol = Optional.of(e.getKey());
        break;
      }
    }
    if (rowTypeCol.isPresent() && !rowTypeCol.equals(rowTypeFile)) {
      LOG.info("Different rowType detected for file {}: {} (filename) vs {} (id terms)", PathUtils.getFilename(schema.file), rowTypeFile, rowTypeCol.get());
    }
    return rowTypeCol.isPresent() ? rowTypeCol : rowTypeFile;
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
    final CsvParserSettings set = CSV.clone();

    String val = unescapeBackslash(attr(parser, "fieldsTerminatedBy"));
    set.setDelimiterDetectionEnabled(true);
    if (val != null) {
      if (val.length() != 1) {
        throw new IllegalArgumentException("fieldsTerminatedBy needs to be a single char");
      } else {
        set.setDelimiterDetectionEnabled(false);
        set.getFormat().setDelimiter(val.charAt(0));
        LOG.debug("Use delimiter {} for {}", StringEscapeUtils.escapeJava(val), rowType);
      }
    }
    val = unescapeBackslash(attr(parser, "fieldsEnclosedBy"));
    set.setQuoteDetectionEnabled(false);
    if (val == null) {
      val = String.valueOf('\0');
    }
    if (val.length() != 1) {
      throw new IllegalArgumentException("fieldsEnclosedBy needs to be a single char");
    } else {
      LOG.debug("Use quote char {} for {}", val, rowType);
      set.getFormat().setQuote(val.charAt(0));
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
    List<Schema.Field> fields = Lists.newArrayList();
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
              buildField(parser, id).ifPresent(fields::add);
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

    Schema s = new Schema(file, rowType, charset, set, fields);
    LOG.debug("Found schema {}", s);
    schemas.put(rowType, s);
  }

  private Optional<Schema.Field> buildField(XMLStreamReader2 parser, boolean id) {
    final Term term = id ? DWCA_ID : VocabularyUtils.TF.findPropertyTerm(attr(parser, "term"));
    try {
      String value = attr(parser, "default");
      Integer index = null;
      String indexAsString = attr(parser, "index");
      if (indexAsString != null) {
        index = Integer.parseInt(indexAsString);
      }
      String delimiter = attr(parser, "delimitedBy");
      return Optional.of(new Schema.Field(term, value, index, delimiter));

    } catch (IllegalArgumentException e) {
      LOG.error("DwC-A descriptor with bad {} field: {}", term, e.getMessage());
      return Optional.empty();
    }
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
    LOG.info("Reading DwC-A descriptor");
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
  }

  /**
   * Override to add dwca default values for missing values
   */
  @Override
  public Stream<TermRecord> stream(Term rowType) {
    final Optional<Term> idTerm = Optional.ofNullable(ROW_TYPE_TO_ID.getOrDefault(rowType, null));
    final Optional<Schema> schema = schema(rowType);
    if (schema.isPresent() && !schema.get().hasTerm(DWCA_ID) && idTerm.isPresent()) {
      return super.stream(rowType)
          .map(row -> {
            // add dwca id columns
            idTerm.ifPresent(term -> row.put(DWCA_ID, row.get(term)));
            return row;
          });
    } else {
      // no extra id column needed, return original stream
      return super.stream(rowType);
    }
  }

  private void validate() throws NormalizationFailedException.SourceInvalidException {
    // no checks
    if (isEmpty()) {
      throw new NormalizationFailedException.SourceInvalidException("No data files found in " + folder);
    }
    if (coreRowType == null) {
      throw new NormalizationFailedException.SourceInvalidException("No core rowType set");
    }
  }

  public void validate(InsertMetadata meta) throws NormalizationFailedException.SourceInvalidException {
    // check for a minimal parsed name
    Optional<Schema> optCore = schema(DwcTerm.Taxon);
    if (!optCore.isPresent()) {
      throw new NormalizationFailedException.SourceInvalidException("No Taxon core, not a checklist?");
    }
    final Schema core = optCore.get();
    if ((core.hasTerm(DwcTerm.genus) || core.hasTerm(GbifTerm.genericName))
        && core.hasTerm(DwcTerm.specificEpithet)
        ) {
      meta.setParsedNameMapped(true);
    }

    // make sure either scientificName or genus & specificEpithet are mapped
    if (!core.hasTerm(DwcTerm.scientificName)) {
      LOG.warn("No scientificName mapped");
      if (!meta.isParsedNameMapped()) {
        // no name to work with!!!
        throw new NormalizationFailedException.SourceInvalidException("No scientificName nor parsed name mapped");
      } else {
        // warn if there is no author mapped for a parsed name
        if (!core.hasTerm(DwcTerm.scientificNameAuthorship)) {
          LOG.warn("No scientificNameAuthorship mapped for parsed name");
        }
      }
    }

    // warn if highly recommended terms are missing
    if (!core.hasTerm(DwcTerm.taxonRank)) {
      LOG.warn("No taxonRank mapped");
    }

    // check if taxonID should be used, not the generic ID
    if (core.hasTerm(DwcTerm.taxonID) && !core.field(DWCA_ID).index.equals(core.field(DwcTerm.taxonID).index)) {
      LOG.info("Use taxonID instead of ID");
      meta.setTaxonId(true);
    }
    // multi values in use, e.g. for acceptedID?
    for (Schema.Field f : core.columns) {
      if (!Strings.isNullOrEmpty(f.delimiter)) {
        meta.getMultiValueDelimiters().put(f.term, Splitter.on(f.delimiter).omitEmptyStrings());
      }
    }
    for (Term t : DwcTerm.HIGHER_RANKS) {
      if (core.hasTerm(t)) {
        meta.setDenormedClassificationMapped(true);
        break;
      }
    }
    if (core.hasTerm(AcefTerm.Superfamily)) {
      meta.setDenormedClassificationMapped(true);
    }
    if (core.hasTerm(DwcTerm.parentNameUsageID) || core.hasTerm(DwcTerm.parentNameUsage)) {
      meta.setParentNameMapped(true);
    }
    if (core.hasTerm(DwcTerm.acceptedNameUsageID) || core.hasTerm(DwcTerm.acceptedNameUsage)) {
      meta.setAcceptedNameMapped(true);
    } else {
      if (core.hasTerm(AcefTerm.AcceptedTaxonID)) {
        // this sometimes gets confused with dwc - translate into dwc as we read dwc archives here
        // as schema and all fields are final we create a copy of the entire schema here
        Schema.Field f = core.field(AcefTerm.AcceptedTaxonID);
        Schema.Field f2 = new Schema.Field(DwcTerm.acceptedNameUsageID, f.value, f.index, f.delimiter);
        List<Schema.Field> updatedColumns = Lists.newArrayList(core.columns);
        updatedColumns.set(updatedColumns.indexOf(f), f2);
        Schema s2 = new Schema(core.file, core.rowType, core.encoding, core.settings, updatedColumns);
        putSchema(s2);
        meta.setAcceptedNameMapped(true);
      } else {
        LOG.warn("No accepted name terms mapped");
      }
    }
    if (core.hasTerm(DwcTerm.originalNameUsageID) || core.hasTerm(DwcTerm.originalNameUsage)) {
      meta.setOriginalNameMapped(true);
    }
    // any classification?
    if (!meta.isParentNameMapped() && !meta.isDenormedClassificationMapped()) {
      LOG.warn("No higher classification mapped");
    }

    //TODO: validate extensions:
    // vernacular name: vernacularName
    // distribution: some area (locationID, countryCode, etc)
  }

}
