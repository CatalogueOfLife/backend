package life.catalogue.csv;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.collection.MapUtils;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.common.io.PathUtils;

import org.gbif.dwc.terms.*;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.text.StringEscapeUtils;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.Format;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParserSettings;

/**
 *
 */
public class DwcaReader extends CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaReader.class);
  public static final String META_FN = "meta.xml";
  private static final String EML_FN = "eml.xml";
  private static final List<Term> PREFERRED_CORE_TYPES = List.of(DwcTerm.Taxon, DwcTerm.Event, DwcTerm.Occurrence);
  private static final XMLInputFactory2 factory;
  
  static {
    factory = (XMLInputFactory2) XMLInputFactory2.newFactory();
    factory.configureForConvenience();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
  }
  
  private static final Map<Term, Term> ROW_TYPE_TO_ID = Map.of(
      DwcTerm.Occurrence, DwcTerm.occurrenceID,
      DwcTerm.Event, DwcTerm.eventID,
      DwcTerm.Taxon, DwcTerm.taxonID,
      DwcTerm.MeasurementOrFact, DwcTerm.measurementID,
      ColdpTerm.NameRelation, DwcTerm.taxonID
  );

  private static final Map<Term, Term> TERM_TO_ROW_TYPE = MapUtils.linkedHashMap(
    DwcTerm.occurrenceID, DwcTerm.Occurrence,
    DwcTerm.measurementID, DwcTerm.MeasurementOrFact,
    ColdpTerm.relatedNameID, ColdpTerm.NameRelation,
    DwcTerm.taxonID, DwcTerm.Taxon,
    DwcTerm.parentNameUsageID, DwcTerm.Taxon,
    DwcTerm.acceptedNameUsageID, DwcTerm.Taxon,
    DwcTerm.originalNameUsageID, DwcTerm.Taxon,
    DwcTerm.nomenclaturalStatus, DwcTerm.Taxon,
    DwcTerm.eventID, DwcTerm.Event
  );

  static {
    // make sure we are aware of ColTerms
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
    TermFactory.instance().registerTerm(DwcaTerm.ID);
  }
  
  private Term coreRowType;
  private Path metadataFile;
  
  private DwcaReader(Path folder) throws IOException {
    super(folder, "dwc", "dwca");
    detectMappedClassification(ColdpTerm.Taxon, ImmutableMap.<Term, Rank>builder()
                                                            .put(DwcTerm.kingdom, Rank.KINGDOM)
                                                            .put(DwcTerm.phylum, Rank.PHYLUM)
                                                            .put(DwcTerm.class_, Rank.CLASS)
                                                            .put(DwcTerm.order, Rank.ORDER)
                                                            .put(DwcTerm.family, Rank.FAMILY)
                                                            .put(DwcTerm.genus, Rank.GENUS)
                                                            .put(DwcTerm.subgenus, Rank.SUBGENUS)
                                                            .build()
    );
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
   *
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   * @throws IOException
   */
  @Override
  protected void discoverSchemas(String termPrefix) throws IOException {
    Path eml = resolve(EML_FN);
    if (Files.exists(eml)) {
      metadataFile = eml;
    }

    Path meta = resolve(META_FN);
    if (Files.exists(meta)) {
      readFromMeta(meta);
      
    } else {
      super.discoverSchemas(termPrefix);
      
      // add artificial id terms for known rowType id pairs
      for (Schema s : schemas.values()) {
        if (!s.hasTerm(DwcaTerm.ID)) {
          Optional<Term> idTerm = Optional.ofNullable(ROW_TYPE_TO_ID.getOrDefault(s.rowType, null));
          if (idTerm.isPresent() && s.hasTerm(idTerm.get())) {
            // create another id field with the same index
            Schema.Field id = new Schema.Field(DwcaTerm.ID, s.field(idTerm.get()).index);
            List<Schema.Field> columns = Lists.newArrayList(s.columns);
            columns.add(id);
            Schema s2 = new Schema(s.files, s.rowType, s.encoding, s.settings, columns);
            updateSchema(s2);
          }
        }
      }
      
      
      // select core
      if (size() == 1) {
        coreRowType = schemas.keySet().iterator().next();

      } else if (size() > 1) {
        for (Term t : PREFERRED_CORE_TYPES) {
          if (hasData(t)) {
            coreRowType = t;
            LOG.warn("{} data files found but no archive descriptor. Using {}", size(), coreRowType);
            break;
          }
        }
        if (coreRowType == null) {
          // rather abort instead of picking randomly
          throw new SourceInvalidException("Multiple unknown schemas found: " + Joiner.on(", ").join(schemas.keySet()));
        }
      }
    }

    if (isEmpty()) {
      throw new SourceInvalidException("No core schema found");
    }

    Format format = coreSchema().settings.getFormat();
    if (format instanceof CsvFormat) {
      CsvFormat f2 = (CsvFormat) format;
      LOG.info("Found {} core [csv delim={} quote={}] and {} extensions", coreRowType, f2.getDelimiter(), f2.getQuote(), size() - 1);
    } else {
      LOG.info("Found {} core [tsv] and {} extensions", coreRowType, size() - 1);
    }
  }
  
  @Override
  protected Optional<Term> detectRowType(Schema schema, String termPrefix) {
    // we only end up here when there is no meta descriptor
    // the default impl derives the rowType from the file name - not very trustworthy
    Optional<Term> rowTypeFile = super.detectRowType(schema, termPrefix);
    // so we check columns for known and indicating terms first
    Optional<Term> rowTypeCol = Optional.empty();
    for (Map.Entry<Term, Term> e : TERM_TO_ROW_TYPE.entrySet()) {
      if (schema.hasTerm(e.getKey())) {
        rowTypeCol = Optional.of(e.getValue());
        break;
      }
    }
    if (rowTypeCol.isPresent() && !rowTypeCol.equals(rowTypeFile)) {
      LOG.info("Different rowType detected for file {}: {} (filename) vs {} (terms)", PathUtils.getFilename(schema.getFirstFile()), rowTypeFile.get(), rowTypeCol.get());
    }
    return rowTypeCol.isPresent() ? rowTypeCol : rowTypeFile;
  }
  
  private void buildSchema(XMLStreamReader2 parser, boolean core) throws XMLStreamException, IOException {
    // rowType
    final Term rowType = VocabularyUtils.TF.findClassTerm(attr(parser, "rowType"));
    if (rowType == null) {
      if (core) {
        throw new IllegalArgumentException("core rowType missing");
      } else {
        LOG.warn("No extension rowType found! Ignore");
        return;
      }
    }
    if (core) {
      coreRowType = rowType;
    }
    
    // encoding
    String enc = attr(parser, "encoding");
    
    // delimiter
    final CommonParserSettings<?> set;
    final CsvParserSettings csvSettings = csvSetting();
    
    String val = unescapeBackslash(attr(parser, "fieldsTerminatedBy"));
    csvSettings.setDelimiterDetectionEnabled(true);
    if (val != null) {
      if (val.length() != 1) {
        throw new IllegalArgumentException("fieldsTerminatedBy needs to be a single char");
      } else {
        csvSettings.setDelimiterDetectionEnabled(false);
        csvSettings.getFormat().setDelimiter(val.charAt(0));
        LOG.debug("Use delimiter {} for {}", StringEscapeUtils.escapeJava(val), rowType);
      }
    }
    val = unescapeBackslash(attr(parser, "fieldsEnclosedBy"));
    csvSettings.setQuoteDetectionEnabled(false);
    if (val == null) {
      val = String.valueOf('\0');
    }
    if (val.length() != 1) {
      throw new IllegalArgumentException("fieldsEnclosedBy needs to be a single char");
    } else {
      LOG.debug("Use quote char {} for {}", val, rowType);
      csvSettings.getFormat().setQuote(val.charAt(0));
    }
    // we ignore linesTerminatedBy
    // Its quite often wrong and people dont really use anything else than \n \r!
    csvSettings.setLineSeparatorDetectionEnabled(true);

    // now decide whether we want TSV or CSV parsing
    if (csvSettings.getFormat().getDelimiter() == '\t' && csvSettings.getFormat().getQuote() == '\0') {
      set = tsvSetting();
    } else {
      set = csvSettings;
    }

    val = attr(parser, "ignoreHeaderLines");
    if (val != null) {
      try {
        set.setNumberOfRowsToSkip(Long.parseLong(val));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("ignoreHeaderLines needs to be a valid integer");
      }
    }
    
    // parse fields & file
    Set<Path> files = new HashSet<>();
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
            case "coreId":
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
              var p = resolve(text.toString());
              if (Files.exists(p)) {
                if (files.contains(p)) {
                  LOG.warn("File location {} is listed multiple times. Ignore redundant copies", p);
                } else {
                  files.add(p);
                }
              } else {
                LOG.warn("DwC-A file location {} does not exist!", p);
              }
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
    // assert we got at least one file
    if (files.isEmpty()) {
      throw new IllegalArgumentException("at least one existing file location must be given for " + rowType);
    }

    // final encoding
    Charset charset;
    try {
      charset = Charset.forName(enc);
    } catch (IllegalArgumentException e) {
      var file = files.iterator().next();
      try (CharsetDetectingStream in = CharsetDetectingStream.create(Files.newInputStream(file))) {
        charset = in.getCharset();
        LOG.debug("Use encoding {} for file {}", charset, PathUtils.getFilename(file));
      }
      LOG.warn("Bad charset encoding {} specified, using {}", enc, charset);
    }
    
    Schema s = new Schema(List.copyOf(files), rowType, charset, set, fields);
    LOG.debug("Found schema {}", s);
    schemas.put(rowType, s);
  }
  
  private Optional<Schema.Field> buildField(XMLStreamReader2 parser, boolean id) {
    final Term term = id ? DwcaTerm.ID : VocabularyUtils.TF.findPropertyTerm(attr(parser, "term"));
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
    try (CharsetDetectingStream stream = CharsetDetectingStream.create(Files.newInputStream(meta))) {
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
      throw new SourceInvalidException("Failed to parse DwC-A descriptor");
    }
  }
  
  /**
   * Override to add dwca default values for missing values
   */
  @Override
  public Stream<VerbatimRecord> stream(Term rowType) {
    final Optional<Term> idTerm = Optional.ofNullable(ROW_TYPE_TO_ID.getOrDefault(rowType, null));
    final Optional<Schema> schema = schema(rowType);
    if (schema.isPresent() && !schema.get().hasTerm(DwcaTerm.ID) && idTerm.isPresent()) {
      return super.stream(rowType)
          .map(row -> {
            // add dwca id columns
            idTerm.ifPresent(term -> row.put(DwcaTerm.ID, row.get(term)));
            return row;
          });
    } else {
      // no extra id column needed, return original stream
      return super.stream(rowType);
    }
  }
  
  @Override
  protected void validate() throws SourceInvalidException {
    super.validate();
    // no checks
    if (coreRowType != DwcTerm.Taxon) {
      throw new SourceInvalidException("No Taxon core, not a checklist?");
    }
    // check for a minimal parsed name
    final Schema core = schema(DwcTerm.Taxon).get();
    if ((core.hasTerm(DwcTerm.genus) || core.hasTerm(DwcTerm.genericName))
        && core.hasTerm(DwcTerm.specificEpithet)
        ) {
      mappingFlags.setParsedNameMapped(true);
    }
    
    // make sure either scientificName or genus & specificEpithet are mapped
    if (!core.hasTerm(DwcTerm.scientificName)) {
      LOG.warn("No scientificName mapped");
      if (!mappingFlags.isParsedNameMapped()) {
        // no name to work with!!!
        throw new SourceInvalidException("No scientificName nor parsed name mapped");
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
    if (core.hasTerm(DwcTerm.taxonID) && (!core.hasTerm(DwcaTerm.ID) || !core.field(DwcaTerm.ID).index.equals(core.field(DwcTerm.taxonID).index))) {
      LOG.info("Use taxonID instead of ID");
      mappingFlags.setTaxonId(true);
    }
    // multi values in use, e.g. for acceptedID?
    for (Schema.Field f : core.columns) {
      if (!Strings.isNullOrEmpty(f.delimiter)) {
        mappingFlags.getMultiValueDelimiters().put(f.term, Splitter.on(f.delimiter).omitEmptyStrings());
      }
    }
    for (Term t : DwcTerm.HIGHER_RANKS) {
      if (core.hasTerm(t)) {
        mappingFlags.setDenormedClassificationMapped(true);
        break;
      }
    }
    if (core.hasTerm(AcefTerm.Superfamily)) {
      mappingFlags.setDenormedClassificationMapped(true);
    }
    if (core.hasTerm(DwcTerm.parentNameUsageID) || core.hasTerm(DwcTerm.parentNameUsage)) {
      mappingFlags.setParentNameMapped(true);
    }
    if (core.hasTerm(DwcTerm.acceptedNameUsageID) || core.hasTerm(DwcTerm.acceptedNameUsage)) {
      mappingFlags.setAcceptedNameMapped(true);
    } else {
      if (core.hasTerm(AcefTerm.AcceptedTaxonID)) {
        // this sometimes gets confused with dwc - translate into dwc as we read dwc archives here
        // as schema and all fields are final we create a copyTaxon of the entire schema here
        Schema.Field f = core.field(AcefTerm.AcceptedTaxonID);
        Schema.Field f2 = new Schema.Field(DwcTerm.acceptedNameUsageID, f.value, f.index, f.delimiter);
        List<Schema.Field> updatedColumns = Lists.newArrayList(core.columns);
        updatedColumns.set(updatedColumns.indexOf(f), f2);
        Schema s2 = new Schema(core.files, core.rowType, core.encoding, core.settings, updatedColumns);
        putSchema(s2);
        mappingFlags.setAcceptedNameMapped(true);
      } else {
        LOG.warn("No accepted name terms mapped");
      }
    }
    if (core.hasTerm(DwcTerm.originalNameUsageID) || core.hasTerm(DwcTerm.originalNameUsage)) {
      mappingFlags.setOriginalNameMapped(true);
    }
    // any classification?
    if (!mappingFlags.isParentNameMapped() && !mappingFlags.isDenormedClassificationMapped()) {
      LOG.warn("No higher classification mapped");
    }
    // metadata file present?
    if (metadataFile != null && !Files.exists(metadataFile)) {
      LOG.warn("Metadata file {} does not exist", metadataFile);
      metadataFile = null;
    }

    //TODO: validate extensions:
    // vernacular name: vernacularName
    // distribution: some area (locationID, countryCode, etc)
  }
  
}
