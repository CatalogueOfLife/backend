package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import life.catalogue.api.model.ReleaseAttempt;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.api.UnparsableNameException;
import org.gbif.nameparser.util.NameFormatter;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.ws.rs.NotFoundException;

/**
 * Service to index a dataset from the Checklist Bank.
 *
 * /data/matching-ws/export/             - CSV exports from the Checklist Bank
 * /data/matching-ws/index/main          - Main lucene index
 * /data/matching-ws/index/identifiers   - Lucene indexes for IDs lookups
 * /data/matching-ws/index/ancillary     - Lucene indexes for status values e.g. IUCN
 */
@Service
public class IndexingService {

  private static final Logger LOG = LoggerFactory.getLogger(IndexingService.class);

  @Value("${index.path:/tmp/matching-index}")
  String indexPath;

  @Value("${export.path:/tmp/matching-export}")
  String exportPath;

  @Value("${temp.path:/tmp/matching-tmp}")
  String tempIndexPath;

  @Value("${clb.url}")
  String clbUrl;

  @Value("${clb.user}")
  String clbUser;

  @Value("${clb.password}")
  String clPassword;

  @Value("${clb.driver}")
  String clDriver;

  @Autowired protected MatchingService matchingService;

  private static final String REL_PATTERN_STR = "(\\d+)(?:LX?RC?|R(\\d+))";
  private static final Pattern REL_PATTERN = Pattern.compile("^" + REL_PATTERN_STR + "$");

  protected static final ScientificNameAnalyzer scientificNameAnalyzer = new ScientificNameAnalyzer();

  protected static IndexWriterConfig getIndexWriterConfig() {
    Map<String, Analyzer> analyzerPerField = new HashMap<>();
    analyzerPerField.put(FIELD_SCIENTIFIC_NAME, new StandardAnalyzer());
    analyzerPerField.put(FIELD_CANONICAL_NAME, scientificNameAnalyzer);
    PerFieldAnalyzerWrapper aWrapper = new PerFieldAnalyzerWrapper( new KeywordAnalyzer(), analyzerPerField);
    return new IndexWriterConfig(aWrapper);
  }

  public Optional<Integer> lookupDatasetKey(SqlSessionFactory factory, String datasetKey) {
    if (life.catalogue.common.text.StringUtils.hasContent(datasetKey)) {
      Matcher m = REL_PATTERN.matcher(datasetKey);
      if (m.find()){
        return Optional.of(releaseKeyFromMatch(factory, m));
      }
    }
    return Optional.empty();
  }

  private Integer releaseKeyFromMatch(SqlSessionFactory factory,  Matcher m) {
    // parsing cannot fail, we have a pattern
    int projectKey = Integer.parseInt(m.group(1));
    Integer releaseKey;
    // candidate requested? (\\d+)(?:LX?RC?|R(\\d+))$
    final boolean extended = m.group().contains("X");
    if (m.group().endsWith("RC")) {

      releaseKey = lookupLatest(factory, projectKey, true, extended);
    } else if (m.group().endsWith("R")) {

      releaseKey = lookupLatest(factory, projectKey, false, extended);

    } else {
      // parsing cannot fail, we have a pattern
      int attempt = Integer.parseInt(m.group(2));
      releaseKey = lookupAttempt(factory, new ReleaseAttempt(projectKey, attempt));
    }

    if (releaseKey == null) {
      throw new NotFoundException("Dataset " + projectKey + " was never released");
    }
    return releaseKey;
  }

  /**
   * @param projectKey a dataset key that is known to exist and point to a managed dataset
   * @return dataset key for the latest release of a project or null in case no release exists
   */
  private Integer lookupLatest(SqlSessionFactory factory, int projectKey, boolean candidate, boolean extended) throws NotFoundException {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetOrigin origin = extended ? DatasetOrigin.XRELEASE : DatasetOrigin.RELEASE;
      return dm.latestRelease(projectKey, !candidate, origin);
    }
  }

  private Integer lookupAttempt(SqlSessionFactory factory,  ReleaseAttempt release) throws NotFoundException {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.releaseAttempt(release.projectKey, release.attempt);
    }
  }

  /**
   * Writes an export of  the name usages in a checklist bank dataset to a CSV file.
   *
   * @param datasetKeyInput
   * @throws Exception
   */
  @Transactional
  public void writeCLBToFile(@NotNull final String datasetKeyInput) throws Exception {

    // I am seeing better results with this MyBatis Pooling DataSource for Cursor queries
    // (parallelism) as opposed to the spring managed DataSource
    final PooledDataSource dataSource = new PooledDataSource(clDriver, clbUrl, clbUser, clPassword);
    // Create a session factory
    SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
    sessionFactoryBean.setDataSource(dataSource);
    SqlSessionFactory factory = sessionFactoryBean.getObject();
    assert factory != null;
    factory.getConfiguration().addMapper(IndexingMapper.class);
    factory.getConfiguration().addMapper(DatasetMapper.class);

    // resolve the magic keys...
    Optional<Integer> datasetKey = Optional.empty();
    try {
      datasetKey = Optional.of(Integer.parseInt(datasetKeyInput));
    } catch (NumberFormatException ignored) {
    }

    if (datasetKey.isEmpty()) {
      datasetKey = lookupDatasetKey(factory, datasetKeyInput);
    }

    if (datasetKey.isEmpty()) {
      throw new IllegalArgumentException("Invalid dataset key: " + datasetKeyInput);
    }

    final Integer validDatasetKey = datasetKey.get();
    final String directory = exportPath + "/" + datasetKeyInput;
    final String fileName = directory + "/" + "index.csv";

    LOG.info("Writing dataset to file {}", fileName);
    final AtomicInteger counter = new AtomicInteger(0);

    FileUtils.forceMkdir(new File(directory));
    try (SqlSession session = factory.openSession(false);
        final ICSVWriter writer = new CSVWriterBuilder(new FileWriter(fileName)).withSeparator('$').build()) {
      StatefulBeanToCsv<NameUsage> sbc = new StatefulBeanToCsvBuilder<NameUsage>(writer)
        .withQuotechar('\'')
        .build();
      // Create index writer
      consume(
          () -> session.getMapper(IndexingMapper.class).getAllForDataset(validDatasetKey),
          name -> {
            try {
              sbc.write(name);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            counter.incrementAndGet();
          });
    } finally {
      dataSource.forceCloseAll();
    }

    // write metadata file in JSON format
    LOG.info("ChecklistBank export written to file {}: {}", fileName, counter.get());
  }

  @Transactional
  public void indexIdentifiers(String datasetKey) throws Exception {
    writeCLBToFile(datasetKey);
    indexFile(exportPath  + "/" + datasetKey, tempIndexPath + "/" + datasetKey);
    writeJoinIndex( tempIndexPath + "/"  + datasetKey, indexPath + "/identifiers/" + datasetKey, false);
  }

  @Transactional
  public void indexIUCN(String datasetKey) throws Exception {
    writeCLBIUCNToFile(datasetKey);
    indexFile(exportPath  + "/" + datasetKey, tempIndexPath + "/" + datasetKey);
    writeJoinIndex( tempIndexPath + "/" + datasetKey, indexPath + "/ancillary/" + datasetKey, true);
  }

  @Transactional
  public void writeCLBIUCNToFile(@NotNull final String datasetKeyInput) throws Exception {

    // I am seeing better results with this MyBatis Pooling DataSource for Cursor queries
    // (parallelism) as opposed to the spring managed DataSource
    PooledDataSource dataSource = new PooledDataSource(clDriver, clbUrl, clbUser, clPassword);
    // Create a session factory
    SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
    sessionFactoryBean.setDataSource(dataSource);
    SqlSessionFactory factory = sessionFactoryBean.getObject();
    assert factory != null;
    factory.getConfiguration().addMapper(IndexingMapper.class);
    factory.getConfiguration().addMapper(DatasetMapper.class);

    // resolve the magic keys...
    Optional<Integer> datasetKey = Optional.empty();
    try {
      datasetKey = Optional.of(Integer.parseInt(datasetKeyInput));
    } catch (NumberFormatException e) {
    }

    if (datasetKey.isEmpty()) {
      datasetKey = lookupDatasetKey(factory, datasetKeyInput);
    }

    if (datasetKey.isEmpty()) {
      throw new IllegalArgumentException("Invalid dataset key: " + datasetKeyInput);
    }

    final Integer validDatasetKey = datasetKey.get();

    LOG.info("Writing dataset to file...");
    final AtomicInteger counter = new AtomicInteger(0);
    final String fileName = exportPath + "/" + datasetKeyInput + "/" + "index.csv";
    FileUtils.forceMkdir(new File(exportPath + "/" + datasetKeyInput));
    try (SqlSession session = factory.openSession(false);
         final ICSVWriter writer = new CSVWriterBuilder(new FileWriter(fileName)).withSeparator('$').build()) {

      final ObjectMapper objectMapper = new ObjectMapper();
      final StatefulBeanToCsv<NameUsage> sbc = new StatefulBeanToCsvBuilder<NameUsage>(writer)
        .withQuotechar('\'')
        .build();

      // Create index writer
      consume(
        () -> session.getMapper(IndexingMapper.class).getAllWithExtensionForDataset(validDatasetKey),
        nameUsage -> {
          try {
            if (StringUtils.isNotBlank(nameUsage.getExtension())){
              // parse it
              JsonNode node = objectMapper.readTree(nameUsage.getExtension());
              nameUsage.setCategory(node.path("iucn:threatStatus").asText());
            }
            sbc.write(nameUsage);

          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          counter.incrementAndGet();
        });
    } finally {
      dataSource.forceCloseAll();
    }

    // write metadata file in JSON format
    LOG.info("ChecklistBank IUCN export written to file {}: {}", fileName, counter.get());
  }

  public static Directory newMemoryIndex(Iterable<NameUsage> usages) throws IOException {
    LOG.info("Start building a new RAM index");
    Directory dir = new ByteBuffersDirectory();
    IndexWriter writer = getIndexWriter(dir);

    // creates initial index segments
    writer.commit();
    int counter = 0;
    for (NameUsage u : usages) {
      if (u != null && u.getId() != null) {
        writer.addDocument(toDoc(u));
        counter ++;
      }
    }
    writer.close();
    LOG.info("Finished building nub index with {} usages", counter);
    return dir;
  }

  private static IndexWriter getIndexWriter(Directory dir) throws IOException {
    return new IndexWriter(dir, getIndexWriterConfig());
  }

  public void writeJoinIndex(String tempIndexPath, String ancillaryIndexPath, boolean acceptedOnly) {

    try {
      // Load temp index directory
      Directory tempDirectory = FSDirectory.open(Paths.get(tempIndexPath));
      IndexReader tempReader = DirectoryReader.open(tempDirectory);
      IndexSearcher searcher = new IndexSearcher(tempReader);

      // Create ancillary index
      Path indexDirectory = initialiseIndexDirectory(ancillaryIndexPath);
      Directory ancillaryDirectory = FSDirectory.open(indexDirectory);
      IndexWriterConfig config = getIndexWriterConfig();
      config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
      IndexWriter ancillaryIndexWriter = new IndexWriter(ancillaryDirectory, getIndexWriterConfig());

      // Construct a simple query to get all documents
      TopDocs results = searcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);
      ScoreDoc[] hits = results.scoreDocs;

      AtomicInteger counter = new AtomicInteger(0);

      // Write document data
      for (ScoreDoc hit : hits) {

        counter.incrementAndGet();
        Document doc = searcher.storedFields().document(hit.doc);
        Map<String, String> hierarchy = loadHierarchy(searcher, doc.get(FIELD_ID));

        String status = doc.get(FIELD_STATUS);
        if (status != null &&
          acceptedOnly &&
          !status.equals(TaxonomicStatus.ACCEPTED.name())) {
          // skip synonyms, otherwise we would index them twice
          continue;
        }
        String scientificName = doc.get(FIELD_SCIENTIFIC_NAME);
        Classification classification = new Classification();
        classification.setKingdom(hierarchy.getOrDefault(Rank.KINGDOM.name(), ""));
        classification.setPhylum(hierarchy.getOrDefault(Rank.PHYLUM.name(), ""));
        classification.setClazz(hierarchy.getOrDefault(Rank.CLASS.name(), ""));
        classification.setOrder(hierarchy.getOrDefault(Rank.ORDER.name(), ""));
        classification.setFamily(hierarchy.getOrDefault(Rank.FAMILY.name(), ""));
        classification.setGenus(hierarchy.getOrDefault(Rank.GENUS.name(), ""));
        classification.setSpecies(hierarchy.getOrDefault(Rank.SPECIES.name(), ""));

        if (counter.get() % 100000 == 0) {
          LOG.info("Indexed: {} taxa", counter.get());
        }

        // match to main dataset
        NameUsageMatch nameUsageMatch = matchingService.match(scientificName, classification, true);
        if (nameUsageMatch.getUsage() != null) {
          doc.add(new StringField(FIELD_JOIN_ID,
            nameUsageMatch.getAcceptedUsage() != null ? nameUsageMatch.getAcceptedUsage().getKey() :
            nameUsageMatch.getUsage().getKey(), Field.Store.YES));
          ancillaryIndexWriter.addDocument(doc);
        }
      }

      // close temp
      tempReader.close();
      tempDirectory.close();

      // close ancillary
      ancillaryIndexWriter.commit();
      ancillaryIndexWriter.forceMerge(1);
      ancillaryIndexWriter.close();
      ancillaryDirectory.close();

      LOG.info("Ancillary index written: {} documents.", counter.get());
    } catch (Exception e) {
      LOG.error("Error writing documents to ancillary index: {}", e.getMessage(), e);
    }
  }

  public Optional<Document> getById(IndexSearcher searcher, String id) {
    Query query = new TermQuery(new Term(FIELD_ID, id));
    try {
      TopDocs docs = searcher.search(query, 3);
      if (docs.totalHits.value > 0) {
        return Optional.of(searcher.storedFields().document(docs.scoreDocs[0].doc));
      } else {
        return Optional.empty();
      }
    } catch (IOException e) {
      LOG.error("Cannot load usage {} from lucene index", id, e);
    }
    return Optional.empty();
  }

  public Map<String, String> loadHierarchy(IndexSearcher searcher, String id) {
    Map<String, String> classification = new HashMap<>();
    while (id != null) {
      Optional<Document> docOpt = getById(searcher, id);
      if (docOpt.isEmpty()) {
        break;
      }
      Document doc = docOpt.get();
      classification.put(doc.get(FIELD_RANK), doc.get(FIELD_CANONICAL_NAME));
      id = doc.get(FIELD_PARENT_ID);
    }
    return classification;
  }

  @Transactional
  public void createMainIndexFromFile(String exportPath, String indexPath) throws Exception {
    indexFile(exportPath, indexPath + "/main");
  }

  private void indexFile(String exportPath, String indexPath) throws Exception {

    // Create index directory
    Path indexDirectory = initialiseIndexDirectory(indexPath);
    Directory directory = FSDirectory.open(indexDirectory);

    // Create index writer configuration
    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    // Create a session factory
    LOG.info("Indexing dataset from CSV...");
    final AtomicInteger counter = new AtomicInteger(0);
    final String filePath = exportPath + "/index.csv";

    try (Reader reader = new FileReader(filePath);
         IndexWriter indexWriter = new IndexWriter(directory, config)) {

      CsvToBean<NameUsage> csvReader = new CsvToBeanBuilder(reader)
        .withType(NameUsage.class)
        .withSeparator('$')
        .withIgnoreLeadingWhiteSpace(true)
        .withIgnoreEmptyLine(true)
        .build();

      Iterator<NameUsage> iterator = csvReader.iterator();

      while (iterator.hasNext()) {
        if (counter.get() % 100000 == 0) {
          LOG.info("Indexed: {} taxa", counter.get());
        }
        NameUsage nameUsage = iterator.next();
        Document doc = toDoc(nameUsage);
        indexWriter.addDocument(doc);
        counter.incrementAndGet();
      }
      LOG.info("Final index commit");
      indexWriter.commit();
      LOG.info("Optimising index....");
      indexWriter.forceMerge(1);
      LOG.info("Optimisation complete.");
    }
    // write metadata file in JSON format
    LOG.info("Taxa indexed: {}", counter.get());
  }

  @Transactional
  public void runDatasetIndexing(final Integer datasetKey) throws Exception {

    // I am seeing better results with this MyBatis Pooling DataSource for Cursor queries
    // (parallelism) as opposed to the spring managed DataSource
    PooledDataSource dataSource = new PooledDataSource(clDriver, clbUrl, clbUser, clPassword);

    // Create index directory
    Path indexDirectory = initialiseIndexDirectory(indexPath);
    Directory directory = FSDirectory.open(indexDirectory);

    // Create index writer configuration
    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    // Create a session factory
    SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
    sessionFactoryBean.setDataSource(dataSource);
    SqlSessionFactory factory = sessionFactoryBean.getObject();
    assert factory != null;
    factory.getConfiguration().addMapper(IndexingMapper.class);

    LOG.info("Indexing dataset...");
    final AtomicInteger counter = new AtomicInteger(0);
    try (SqlSession session = factory.openSession(false);
        IndexWriter indexWriter = new IndexWriter(directory, config)) {

      // Create index writer
      consume(
          () -> session.getMapper(IndexingMapper.class).getAllForDataset(datasetKey),
          name -> {
            try {
              if (counter.get() % 10000 == 0) {
                LOG.info("Indexed: {} taxa", counter.get());
              }
              Document doc = toDoc(name);
              indexWriter.addDocument(doc);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            counter.incrementAndGet();
          });
      LOG.info("Final index commit");
      indexWriter.commit();
      LOG.info("Optimising index....");
      indexWriter.forceMerge(1);
      LOG.info("Optimisation complete.");
    }
    // write metadata file in JSON format
    LOG.info("Indexed: {}", counter.get());
  }

  private @NotNull Path initialiseIndexDirectory(String indexPath) throws IOException {
    if (new File(indexPath).exists()) {
      FileUtils.forceDelete(new File(indexPath));
    }
    return Paths.get(indexPath);
  }

  protected static Document toDoc(NameUsage nameUsage) {

    Document doc = new Document();
    /*
     Porting notes: The canonical name *sensu strictu* with nothing else but three name parts at
     most (genus, species, infraspecific). No rank or hybrid markers and no authorship,
     cultivar or strain information. Infrageneric names are represented without a
     leading genus. Unicode characters are replaced by their matching ASCII characters."
    */
     Rank rank = Rank.valueOf(nameUsage.getRank());

    Optional<String> optCanonical = Optional.empty();
    try {
      NomCode nomCode = null;
      if (!StringUtils.isEmpty(nameUsage.getNomenclaturalCode())) {
        nomCode = NomCode.valueOf(nameUsage.getNomenclaturalCode());
      }
      ParsedName pn = NameParsers.INSTANCE.parse(nameUsage.getScientificName(), rank, nomCode);

      // canonicalMinimal will construct the name without the hybrid marker and authorship
      String canonical = NameFormatter.canonicalMinimal(pn);
      optCanonical = Optional.ofNullable(canonical);
    } catch (UnparsableNameException | InterruptedException e) {
      // do nothing
      LOG.debug("Unable to parse name to create canonical: {}", nameUsage.getScientificName());
    }

    final String canonical = optCanonical.orElse(nameUsage.getScientificName());

    // use custom precision step as we do not need range queries and prefer to save memory usage
    // instead
    doc.add(new StringField(FIELD_ID, nameUsage.getId(), Field.Store.YES));

    // we only store accepted key, no need to index it
    // If the name is a synonym, then parentId name usage points
    // to the accepted name
    if (StringUtils.isNotBlank(nameUsage.getStatus())
        && nameUsage.getStatus().equals(TaxonomicStatus.SYNONYM.name())
        && nameUsage.getParentId() != null) {
      doc.add(new StringField(FIELD_ACCEPTED_ID, nameUsage.getParentId(), Field.Store.YES));
    }

    // analyzed name field - this is what we search upon
    doc.add(new TextField(FIELD_CANONICAL_NAME, canonical, Field.Store.YES));

    // store full name and classification only to return a full match object for hits
    String nameComplete = nameUsage.getScientificName();
    if (StringUtils.isNotBlank(nameUsage.getAuthorship())) {
      nameComplete += " " + nameUsage.getAuthorship();
    }
    doc.add(new TextField(FIELD_SCIENTIFIC_NAME, nameComplete, Field.Store.YES));

    // this lucene index is not persistent, so not risk in changing ordinal numbers
    doc.add(new StringField(FIELD_RANK, nameUsage.getRank(), Field.Store.YES));

    if (StringUtils.isNotBlank(nameUsage.getParentId()) && !nameUsage.getParentId().equals(nameUsage.getId())) {
      doc.add(new StringField(FIELD_PARENT_ID, nameUsage.getParentId(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getStatus())) {
      doc.add(new StringField(FIELD_STATUS, nameUsage.getStatus(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getCategory())) {
      doc.add(new StringField(FIELD_CATEGORY, nameUsage.getCategory(), Field.Store.YES));
    }

    return doc;
  }

  public static <T> void consume(Supplier<Cursor<T>> cursorSupplier, Consumer<T> handler) {
    try (Cursor<T> cursor = cursorSupplier.get()) {
      cursor.forEach(handler);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
