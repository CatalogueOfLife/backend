package life.catalogue.matching.service;

import static life.catalogue.matching.util.IndexConstants.*;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.bean.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ReleaseAttempt;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.db.DatasetMapper;
import life.catalogue.matching.index.ScientificNameAnalyzer;
import life.catalogue.matching.model.Classification;
import life.catalogue.matching.model.StoredParsedName;
import life.catalogue.matching.model.Dataset;
import life.catalogue.matching.model.NameUsage;
import life.catalogue.matching.model.NameUsageMatch;
import life.catalogue.matching.util.NameParsers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * Service to index a dataset from the Checklist Bank.
 * /data/matching-ws/export/             - CSV exports from the Checklist Bank
 * /data/matching-ws/index/main          - Main lucene index
 * /data/matching-ws/index/identifiers   - Lucene indexes for IDs lookups
 * /data/matching-ws/index/ancillary     - Lucene indexes for status values e.g. IUCN
 */
@Service
@Slf4j
public class IndexingService {

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

  @Value("${indexing.threads:6}")
  Integer indexingThreads;

  @Value("${indexing.fetchsize:50000}")
  Integer dbFetchSize;

  protected final MatchingService matchingService;

  protected static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String REL_PATTERN_STR = "(\\d+)(?:LX?RC?|R(\\d+))";
  private static final Pattern REL_PATTERN = Pattern.compile("^" + REL_PATTERN_STR + "$");

  protected static final ScientificNameAnalyzer scientificNameAnalyzer = new ScientificNameAnalyzer();

  public IndexingService(MatchingService matchingService) {
    this.matchingService = matchingService;
  }

  protected static IndexWriterConfig getIndexWriterConfig() {
    Map<String, Analyzer> analyzerPerField = new HashMap<>();
    analyzerPerField.put(FIELD_SCIENTIFIC_NAME, new StandardAnalyzer());
    analyzerPerField.put(FIELD_CANONICAL_NAME, scientificNameAnalyzer);
    PerFieldAnalyzerWrapper aWrapper = new PerFieldAnalyzerWrapper( new KeywordAnalyzer(), analyzerPerField);
    return new IndexWriterConfig(aWrapper);
  }

  /**
   * Looks up a dataset by its key with support for release keys
   * @param factory MyBatis session factory
   * @param datasetKeyInput a dataset key or a release key
   * @return the dataset or empty if not found
   */
  private Optional<Dataset> lookupDataset(SqlSessionFactory factory, String datasetKeyInput) {

    // resolve the magic keys...
    Optional<Integer> datasetKey = Optional.empty();
    try {
      datasetKey = Optional.of(Integer.parseInt(datasetKeyInput));
      return lookupDataset(factory, datasetKey.get());
    } catch (NumberFormatException e) {
    }

    // otherwise, resolve magic key
    if (datasetKey.isEmpty()) {
      Matcher m = REL_PATTERN.matcher(datasetKeyInput);
      if (m.find()){
        Integer releaseDatasetKey = releaseKeyFromMatch(factory, m);
        return lookupDataset(factory, releaseDatasetKey);

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

  private Optional<Dataset> lookupDataset(SqlSessionFactory factory, Integer key) throws NotFoundException {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return  dm.getDataset(key);
    }
  }

  /**
   * Writes an export of the name usages in a checklist bank dataset to a CSV file.
   *
   * @param datasetKeyInput a dataset key or a release key
   * @throws Exception if the dataset key is invalid or the export fails
   */
  @Transactional
  public void writeCLBToFile(@NotNull final String datasetKeyInput) throws Exception {

    final String directory = exportPath + "/" + datasetKeyInput;
    final String fileName = directory + "/" + INDEX_CSV;
    Path path = Paths.get(fileName);

    if (Files.exists(path) && Files.size(path) > 0) {
      log.info("File {} already exists, skipping export", fileName);
      return;
    }

    try (HikariDataSource dataSource = getDataSource()) {
      final SqlSessionFactory factory = getSqlSessionFactory(dataSource);
      // resolve the magic keys...
      Optional<Dataset> dataset = lookupDataset(factory, datasetKeyInput);
      if (dataset.isEmpty()) {
        throw new IllegalArgumentException("Invalid dataset key: " + datasetKeyInput);
      }

      final AtomicInteger counter = new AtomicInteger(0);

      FileUtils.forceMkdir(new File(directory));

      final String metadata = directory + "/" + METADATA_JSON;
      ObjectMapper mapper = new ObjectMapper();
      mapper.writeValue(new File(metadata), dataset.get());

      log.info("Writing dataset to file {}", fileName);
      final String query = "SELECT " +
        "nu.id as id, " +
        "nu.parent_id as parentId, " +
        "n.scientific_name as scientificName, " +
        "n.authorship as authorship, " +
        "n.rank as rank, " +
        "nu.status as status, " +
        "n.code as nomenclaturalCode, " +
        "'' as extension, " +
        "'' as category " +
        "FROM name_usage nu " +
        "INNER JOIN " +
        "name n on n.id = nu.name_id AND n.dataset_key = " + dataset.get().getKey() +
        " WHERE " +
        "nu.dataset_key = " + dataset.get().getKey();

      try (
        final ICSVWriter writer = new CSVWriterBuilder(new FileWriter(fileName)).withSeparator('$').build();
        Connection conn = dataSource.getConnection();
        Statement st = conn.createStatement()) {

        conn.setAutoCommit(false);
        st.setFetchSize(dbFetchSize);

        StatefulBeanToCsv<NameUsage> sbc = new StatefulBeanToCsvBuilder<NameUsage>(writer)
          .withQuotechar('\'')
          .build();

        try (ResultSet rs = st.executeQuery(query)) {
          while (rs.next()) {
            NameUsage name = new NameUsage(
              rs.getString("id"),
              rs.getString("parentId"),
              rs.getString("scientificName"),
              rs.getString("authorship"),
              rs.getString("status"),
              rs.getString("rank"),
              rs.getString("nomenclaturalCode"),
              rs.getString("category"),
              rs.getString("extension")
            );

            sbc.write(cleanNameUsage(name));
            counter.incrementAndGet();
          }
        }
      }
      // write metadata file in JSON format
      log.info("ChecklistBank export written to file {}: {}", fileName, counter.get());
    }
  }

  private static SqlSessionFactory getSqlSessionFactory(DataSource dataSource) throws Exception {
    final SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
    sessionFactoryBean.setDataSource(dataSource);
    SqlSessionFactory factory = sessionFactoryBean.getObject();
    assert factory != null;
    factory.getConfiguration().addMapper(DatasetMapper.class);
    return factory;
  }

  private HikariDataSource getDataSource() {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(clbUrl);
    hikariConfig.setUsername(clbUser);
    hikariConfig.setPassword(clPassword);
    return new HikariDataSource(hikariConfig);
  }

  private NameUsage cleanNameUsage(NameUsage name) {
    if (name.getRank() == null) {
      name.setRank(Rank.UNRANKED.toString());
    }
    name.setScientificName(name.getScientificName().replaceAll("[/\\\\]", "").trim());
    return name;
  }

  @Transactional
  public void indexIdentifiers(@NotNull String datasetKey) throws Exception {
    if (StringUtils.isBlank(datasetKey)) {
      return;
    }
    final String joinIndexPath = indexPath + "/" + IDENTIFIERS_DIR + "/" + datasetKey;
    if (indexExists(joinIndexPath)){
      log.info("Index for dataset {} available", datasetKey);
      return;
    }

    writeCLBToFile(datasetKey);
    indexFile(exportPath  + "/" + datasetKey, tempIndexPath + "/" + datasetKey);
    writeJoinIndex( tempIndexPath + "/"  + datasetKey, joinIndexPath, false);
  }

  @Transactional
  public void indexIUCN(@NotNull String datasetKey) throws Exception {
    final String joinIndexPath = indexPath + "/" + ANCILLARY_DIR + "/" + datasetKey;
    if (indexExists(joinIndexPath)){
      log.info("Index for dataset {} available", datasetKey);
      return;
    }

    if (StringUtils.isBlank(datasetKey)) {
      return;
    }
    writeCLBIUCNToFile(datasetKey);
    indexFile(exportPath  + "/" + datasetKey, tempIndexPath + "/" + datasetKey);
    writeJoinIndex( tempIndexPath + "/" + datasetKey, joinIndexPath, true);
  }

  private static boolean indexExists(String joinIndexPath) throws IOException {
    Path path = Paths.get(joinIndexPath);
    if (Files.exists(path) && Files.isDirectory(path)) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
        // Check if the directory is non-empty by checking if there's at least one entry
        boolean isEmpty = !stream.iterator().hasNext();
        if (isEmpty) {
          return false;
        }
      }
    }
    Path metadataPath = Paths.get(joinIndexPath + "/" + METADATA_JSON);
    if (!Files.exists(metadataPath) || Files.size(metadataPath) == 0) {
      return false;
    }

    // check for metadata file
    return true;
  }

  @Transactional
  public void writeCLBIUCNToFile(@NotNull final String datasetKeyInput) throws Exception {

    final String fileName = exportPath + "/" + datasetKeyInput + "/" + INDEX_CSV;
    if (Files.exists(Paths.get(fileName))) {
      log.info("File {} already exists, skipping export", fileName);
      return;
    }

    try (HikariDataSource dataSource = getDataSource()) {
      // Create a session factory
      final SqlSessionFactory factory = getSqlSessionFactory(dataSource);

      // resolve the magic keys...
      Optional<Dataset> dataset = lookupDataset(factory, datasetKeyInput);
      if (dataset.isEmpty()) {
        throw new IllegalArgumentException("Invalid dataset key: " + datasetKeyInput);
      }

      final AtomicInteger counter = new AtomicInteger(0);

      final String metadata = exportPath + "/" + datasetKeyInput + "/" + METADATA_JSON;

      FileUtils.forceMkdir(new File(exportPath + "/" + datasetKeyInput));

      log.info("Writing dataset to file {}", fileName);

      // write metadata to file
      ObjectMapper mapper = new ObjectMapper();
      mapper.writeValue(new File(metadata), dataset.get());

      final String query = "SELECT " +
        "nu.id as id, " +
        "nu.parent_id as parentId, " +
        "n.scientific_name as scientificName, " +
        "n.authorship as authorship, " +
        "n.rank as rank, " +
        "nu.status as status, " +
        "n.code as nomenclaturalCode, " +
        "v.terms as extension, " +
        "'' as category " +
        "FROM " +
        "name_usage nu " +
        " INNER JOIN " +
        "name n on n.id = nu.name_id AND n.dataset_key = " + dataset.get().getKey() +
        " LEFT JOIN " +
        "distribution d on d.taxon_id = nu.id AND d.dataset_key = " + dataset.get().getKey() +
        " LEFT JOIN " +
        "verbatim v on v.id = d.verbatim_key AND v.dataset_key = " + dataset.get().getKey() +
        " WHERE " +
        "nu.dataset_key = " + dataset.get().getKey();

      try (
        final ICSVWriter writer = new CSVWriterBuilder(new FileWriter(fileName)).withSeparator('$').build();
        Connection conn = dataSource.getConnection();
        Statement st = conn.createStatement()) {

        conn.setAutoCommit(false);
        st.setFetchSize(dbFetchSize);
        final ObjectMapper objectMapper = new ObjectMapper();

        StatefulBeanToCsv<NameUsage> sbc = new StatefulBeanToCsvBuilder<NameUsage>(writer)
          .withQuotechar('\'')
          .build();

        try (ResultSet rs = st.executeQuery(query)) {
          while (rs.next()) {
            NameUsage nameUsage = new NameUsage(
              rs.getString("id"),
              rs.getString("parentId"),
              rs.getString("scientificName"),
              rs.getString("authorship"),
              rs.getString("status"),
              rs.getString("rank"),
              rs.getString("nomenclaturalCode"),
              rs.getString("category"),
              rs.getString("extension")
            );
            try {
              if (StringUtils.isNotBlank(nameUsage.getExtension())) {
                // parse it
                JsonNode node = objectMapper.readTree(nameUsage.getExtension());
                nameUsage.setCategory(node.path(IUCN_THREAT_STATUS).asText());
              }
              sbc.write(nameUsage);
              counter.incrementAndGet();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
      // write metadata file in JSON format
      log.info("ChecklistBank IUCN export written to file {}: {}", fileName, counter.get());
    }
  }

  public static Directory newMemoryIndex(Iterable<NameUsage>... usages) throws IOException {
    log.info("Start building a new RAM index");
    Directory dir = new ByteBuffersDirectory();
    IndexWriter writer = getIndexWriter(dir);

    // creates initial index segments
    writer.commit();
    int counter = 0;
    for (var iter : usages) {
      for (NameUsage u : iter) {
        if (u != null && u.getId() != null) {
          writer.addDocument(toDoc(u));
          counter ++;
        }
      }
    }
    writer.close();
    log.info("Finished building nub index with {} usages", counter);
    return dir;
  }

  private static IndexWriter getIndexWriter(Directory dir) throws IOException {
    return new IndexWriter(dir, getIndexWriterConfig());
  }

  /**
   * Creates a join index from a temp index and the main index. Essentially the same as the temp index with an
   * additional field that links to the main index.
   *
   * @param tempNameUsageIndexPath  the temp index directory to use
   * @param joinIndexPath the directory to write the join index to
   * @param acceptedOnly if true only accepted taxa are indexed
   */
  private void writeJoinIndex(String tempNameUsageIndexPath, String joinIndexPath, boolean acceptedOnly) {

    try {
      // Load temp index directory
      Directory tempDirectory = FSDirectory.open(Paths.get(tempNameUsageIndexPath));

      // Create ancillary index
      Path indexDirectory = initialiseIndexDirectory(joinIndexPath);
      Directory ancillaryDirectory = FSDirectory.open(indexDirectory);

      // create the join index
      Long[] counters = createJoinIndex(matchingService, tempDirectory, ancillaryDirectory, acceptedOnly, true, indexingThreads);

      // load export metadata
      ObjectMapper mapper = new ObjectMapper();
      Dataset metadata = mapper.readValue(
        new FileReader(tempNameUsageIndexPath + "/" + METADATA_JSON),
        Dataset.class);
      metadata.setTaxonCount(counters[0]);
      metadata.setMatchesToMainIndex(counters[1]);

      // write new metadata with counts
      mapper.writeValue(new File(joinIndexPath + "/" + METADATA_JSON), metadata);

      log.info("Ancillary index written: {} documents, {} matched to " + MAIN_INDEX_DIR + " index", counters[0], counters[1]);
    } catch (Exception e) {
      log.error("Error writing documents to " + ANCILLARY_DIR + " index: {}", e.getMessage(), e);
    }
  }

  /**
   * Create a join index from a temp index and the main index
   *
   * @param matchingService the matching service to use
   * @param tempUsageIndexDirectory the temp index directory to use
   * @param outputDirectory the directory to write the join index to
   * @param acceptedOnly if true only accepted taxa are indexed
   * @param closeDirectoryOnExit if true the output directory will be closed on exit
   * @param indexingThreads the number of threads to use for indexing
   * @return the number of documents indexed and the number of documents matched to the main index
   * @throws IOException if the index cannot be created
   */
  public static Long[] createJoinIndex(MatchingService matchingService,
                                       Directory tempUsageIndexDirectory,
                                       Directory outputDirectory,
                                       boolean acceptedOnly,
                                       boolean closeDirectoryOnExit, int indexingThreads)
    throws IOException {

    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
    IndexWriter joinIndexWriter = new IndexWriter(outputDirectory, getIndexWriterConfig());

    // Construct a simple query to get all documents
    IndexReader tempReader = DirectoryReader.open(tempUsageIndexDirectory);
    IndexSearcher searcher = new IndexSearcher(tempReader);
    TopDocs results = searcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);
    ScoreDoc[] hits = results.scoreDocs;

    AtomicLong counter = new AtomicLong(0);
    AtomicLong matchedCounter = new AtomicLong(0);

    ExecutorService exec = new ThreadPoolExecutor(indexingThreads, indexingThreads,
      5000L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<Runnable>(indexingThreads * 2, true),
      new ThreadPoolExecutor.CallerRunsPolicy());

    List<Document> batch = new ArrayList<>();
    // Write document data
    for (ScoreDoc hit : hits) {

      counter.incrementAndGet();
      Document doc = searcher.storedFields().document(hit.doc);
      batch.add(doc);

      if (batch.size() >= 100000) {
        log.info("Starting batch: {} taxa", counter.get());
        List<Document> finalBatch = batch;
        exec.submit(new JoinIndexTask(matchingService, searcher, joinIndexWriter, finalBatch, acceptedOnly, matchedCounter));
        batch = new ArrayList<>();
      }
    }

    //final batch
    exec.submit(new JoinIndexTask(matchingService, searcher, joinIndexWriter, batch, acceptedOnly, matchedCounter));

    log.info("Finished reading CSV file. Creating join index...");

    exec.shutdown();
    try {
      if (!exec.awaitTermination(60, TimeUnit.MINUTES)) {
        log.error("Forcing shut down of executor service, pending tasks will be lost! {}", exec);
        exec.shutdownNow();
      }
    } catch (InterruptedException var4) {
      log.error("Forcing shut down of executor service, pending tasks will be lost! {}", exec);
      exec.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // close temp
    tempReader.close();
    tempUsageIndexDirectory.close();

    // close ancillary
    joinIndexWriter.commit();
    joinIndexWriter.forceMerge(1);
    joinIndexWriter.close();

    if (closeDirectoryOnExit) {
      outputDirectory.close();
    }

    return new Long[]{counter.get(), matchedCounter.get()};
  }

  static class JoinIndexTask implements Runnable {
    private final IndexWriter writer;
    private final List<Document> docs;
    private final IndexSearcher searcher;
    private final boolean acceptedOnly;
    private final MatchingService matchingService;
    private final AtomicLong matchedCounter;

    public JoinIndexTask(MatchingService matchingService, IndexSearcher searcher, IndexWriter writer, List<Document> docs,
                         boolean acceptedOnly, AtomicLong matchedCounter) {
      this.searcher = searcher;
      this.writer = writer;
      this.docs = docs;
      this.acceptedOnly = acceptedOnly;
      this.matchingService = matchingService;
      this.matchedCounter = matchedCounter;
    }

    @Override
    public void run() {
      try {
        for (Document doc : docs) {

          Map<String, String> hierarchy = loadHierarchy(searcher, doc.get(FIELD_ID));
          String scientificName = doc.get(FIELD_SCIENTIFIC_NAME);
          String status = doc.get(FIELD_STATUS);
          if (acceptedOnly && !isAccepted(status)) {
            // skip synonyms, otherwise we would index them twice
            continue;
          }

          Classification classification = new Classification();
          classification.setKingdom(hierarchy.getOrDefault(Rank.KINGDOM.name(), ""));
          classification.setPhylum(hierarchy.getOrDefault(Rank.PHYLUM.name(), ""));
          classification.setClazz(hierarchy.getOrDefault(Rank.CLASS.name(), ""));
          classification.setOrder(hierarchy.getOrDefault(Rank.ORDER.name(), ""));
          classification.setFamily(hierarchy.getOrDefault(Rank.FAMILY.name(), ""));
          classification.setGenus(hierarchy.getOrDefault(Rank.GENUS.name(), ""));
          classification.setSpecies(hierarchy.getOrDefault(Rank.SPECIES.name(), ""));

          // match to main dataset
          try {
            // use strict matching for classification to classification matching
            NameUsageMatch nameUsageMatch = matchingService.match(scientificName, classification, true);
            if (nameUsageMatch.getUsage() != null) {
              doc.add(new StringField(FIELD_JOIN_ID,
                nameUsageMatch.getAcceptedUsage() != null ? nameUsageMatch.getAcceptedUsage().getKey() :
                  nameUsageMatch.getUsage().getKey(), Field.Store.YES)
              );

              // reduce the side of these indexes by removing the parsed name
              doc.removeField(FIELD_PARSED_NAME_JSON);

              writer.addDocument(doc);
              matchedCounter.incrementAndGet();
            } else {
              log.debug("No match for {}", scientificName);
            }
          } catch (Exception e) {
            log.error("Problem matching name from " + ANCILLARY_DIR + " index " + scientificName, e.getMessage(), e);
          }
        }
        writer.flush();
      } catch (IOException e) {
        log.error("Error writing documents to " + ANCILLARY_DIR + " index: {}", e.getMessage(), e);
      }
    }

    private boolean isAccepted(String status) {
      return status != null && status.equals(TaxonomicStatus.ACCEPTED.name());
    }
  }

  private static Optional<Document> getById(IndexSearcher searcher, String id) {
    Query query = new TermQuery(new Term(FIELD_ID, id));
    try {
      TopDocs docs = searcher.search(query, 3);
      if (docs.totalHits.value > 0) {
        return Optional.of(searcher.storedFields().document(docs.scoreDocs[0].doc));
      } else {
        return Optional.empty();
      }
    } catch (IOException e) {
      log.error("Cannot load usage {} from lucene index", id, e);
    }
    return Optional.empty();
  }

  public static Map<String, String> loadHierarchy(IndexSearcher searcher, String id) {
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
  public void createMainIndex(String datasetId) throws Exception {
    final String mainIndexPath = indexPath + "/" + MAIN_INDEX_DIR;
    if (indexExists(mainIndexPath)){
      log.info("Main index already exists at path {}", mainIndexPath);
      return;
    }
    writeCLBToFile(datasetId);
    indexFile(exportPath + "/" + datasetId, mainIndexPath);
  }

  private void indexFile(String exportPath, String indexPath) throws Exception {

    // Create index directory
    Path indexDirectory = initialiseIndexDirectory(indexPath);
    Directory directory = FSDirectory.open(indexDirectory);

    // Create index writer configuration
    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    // Create a session factory
    log.info("Indexing dataset from CSV...");
    final AtomicLong counter = new AtomicLong(0);
    final String filePath = exportPath + "/" + INDEX_CSV;
    final String metadataPath = exportPath + "/" + METADATA_JSON;

    ExecutorService exec = new ThreadPoolExecutor(indexingThreads, indexingThreads,
      5000L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<Runnable>(indexingThreads * 2, true), new ThreadPoolExecutor.CallerRunsPolicy());

    try (Reader reader = new FileReader(filePath);
         IndexWriter indexWriter = new IndexWriter(directory, config)) {

      CsvToBean<NameUsage> csvReader = new CsvToBeanBuilder(reader)
        .withType(NameUsage.class)
        .withSeparator('$')
        .withMultilineLimit(1)
        .withIgnoreLeadingWhiteSpace(true)
        .withIgnoreEmptyLine(true)
        .build();

      Iterator<NameUsage> iterator = csvReader.iterator();
      List<NameUsage> batch = new ArrayList<>();
      while (iterator.hasNext()) {
        NameUsage nameUsage = iterator.next();
        counter.incrementAndGet();
        batch.add(nameUsage);
        if (batch.size() >= 100000) {
          List<NameUsage> finalBatch = batch;
          exec.submit(new IndexingTask(indexWriter, finalBatch));
          batch = new ArrayList<>();
        }
      }

      //final batch
      exec.submit(new IndexingTask(indexWriter, batch));

      log.info("Finished reading CSV file. Indexing re" + MAIN_INDEX_DIR + "ing taxa...");

      exec.shutdown();
      try {
        if (!exec.awaitTermination(60, TimeUnit.MINUTES)) {
          log.error("Forcing shut down of executor service, pending tasks will be lost! {}", exec);
          exec.shutdownNow();
        }
      } catch (InterruptedException var4) {
        log.error("Forcing shut down of executor service, pending tasks will be lost! {}", exec);
        exec.shutdownNow();
        Thread.currentThread().interrupt();
      }

      finishIndex(indexWriter);
    }

    // write metadata file in JSON format
    log.info("Taxa indexed: {}", counter.get());

    // load export metadata
    ObjectMapper mapper = new ObjectMapper();
    Dataset metadata = mapper.readValue(new FileReader(metadataPath), Dataset.class);
    metadata.setTaxonCount(counter.get());
    metadata.setMatchesToMainIndex(counter.get());

    // write new metadata with counts
    mapper.writeValue(new File(indexPath + "/" + METADATA_JSON), metadata);
  }

  static class IndexingTask implements Runnable {
    private final IndexWriter writer;
    private final List<NameUsage> nameUsages;

    public IndexingTask(IndexWriter writer, List<NameUsage> nameUsages) {
      this.writer = writer;
      this.nameUsages = nameUsages;
    }

    @Override
    public void run() {
      try {
        for (NameUsage nameUsage : nameUsages) {
          Document doc = toDoc(nameUsage);
          writer.addDocument(doc);
        }
        writer.flush();
        nameUsages.clear();
      } catch (IOException e) {
        log.error("Problem writing document to index: {}", e.getMessage(), e);
      }
    }
  }

  private static void finishIndex(IndexWriter indexWriter) throws IOException {
    log.info("Final index commit");
    indexWriter.commit();
    log.info("Optimising index....");
    indexWriter.forceMerge(1);
    log.info("Optimisation complete.");
  }

  private @NotNull Path initialiseIndexDirectory(String indexPath) throws IOException {
    if (new File(indexPath).exists()) {
      FileUtils.forceDelete(new File(indexPath));
    }
    return Paths.get(indexPath);
  }

  /**
   * Generate the lucene document for a name usage
   * @param nameUsage to convert to lucene document
   * @return lucene document
   */
  protected static Document toDoc(NameUsage nameUsage) {

    Document doc = new Document();
    /*
     Porting notes: The canonical name *sensu strictu* with nothing else but three name parts at
     most (genus, species, infraspecific). No rank or hybrid markers and no authorship,
     cultivar or strain information. Infrageneric names are represented without a
     leading genus. Unicode characters are replaced by their matching ASCII characters.
    */
    Rank rank = Rank.valueOf(nameUsage.getRank());

    Optional<String> optCanonical = Optional.empty();
    ParsedName pn = null;
    NomCode nomCode = null;
    try {
      if (!StringUtils.isEmpty(nameUsage.getNomenclaturalCode())) {
        nomCode = NomCode.valueOf(nameUsage.getNomenclaturalCode());
      }
      pn = NameParsers.INSTANCE.parse(nameUsage.getScientificName(), rank, nomCode);
      // canonicalMinimal will construct the name without the hybrid marker and authorship
      String canonical = NameFormatter.canonicalMinimal(pn);
      optCanonical = Optional.ofNullable(canonical);
    } catch (UnparsableNameException | InterruptedException e) {
      // do nothing
      log.debug("Unable to parse name to create canonical: {}", nameUsage.getScientificName());
    }

    if (pn != null){
      try {
        // if there an authorship, reparse with it to get the component authorship parts
        StoredParsedName storedParsedName = StringUtils.isBlank(nameUsage.getAuthorship()) ?
          getStoredParsedName(pn) : constructParsedName(nameUsage, rank, nomCode);
        // store the parsed name components in JSON
        doc.add(new StoredField(
          FIELD_PARSED_NAME_JSON,
          MAPPER.writeValueAsString(storedParsedName))
        );
      } catch (UnparsableNameException | InterruptedException e) {
        // do nothing
        log.debug("Unable to parse name to create canonical: {}", nameUsage.getScientificName());
      } catch ( JsonProcessingException e) {
        // do nothing
        log.debug("Unable to parse name to create canonical: {}", nameUsage.getScientificName());
      }
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
      doc.add(new TextField(FIELD_AUTHORSHIP, nameUsage.getAuthorship(), Field.Store.YES));
    }

    doc.add(new TextField(FIELD_SCIENTIFIC_NAME, nameComplete, Field.Store.YES));

    // this lucene index is not persistent, so not risk in changing ordinal numbers
    doc.add(new StringField(FIELD_RANK, nameUsage.getRank(), Field.Store.YES));

    if (StringUtils.isNotBlank(nameUsage.getParentId()) && !nameUsage.getParentId().equals(nameUsage.getId())) {
      doc.add(new StringField(FIELD_PARENT_ID, nameUsage.getParentId(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getNomenclaturalCode())) {
      doc.add(new StringField(FIELD_NOMENCLATURAL_CODE, nameUsage.getNomenclaturalCode(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getStatus())) {
      doc.add(new StringField(FIELD_STATUS, nameUsage.getStatus(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getCategory())) {
      doc.add(new StringField(FIELD_CATEGORY, nameUsage.getCategory(), Field.Store.YES));
    }

    return doc;
  }

  @NotNull
  private static StoredParsedName constructParsedName(NameUsage nameUsage, Rank rank, NomCode nomCode) throws UnparsableNameException, InterruptedException {
    ParsedName pn = !StringUtils.isBlank(nameUsage.getAuthorship()) ?
      NameParsers.INSTANCE.parse(nameUsage.getScientificName() + " " + nameUsage.getAuthorship(), rank, nomCode)
      : NameParsers.INSTANCE.parse(nameUsage.getScientificName(), rank, nomCode);
    return getStoredParsedName(pn);
  }

  @NotNull
  private static StoredParsedName getStoredParsedName(ParsedName pn) {
    StoredParsedName storedParsedName = new StoredParsedName();
    storedParsedName.setAbbreviated(pn.isAbbreviated());
    storedParsedName.setAutonym(pn.isAutonym());
    storedParsedName.setBinomial(pn.isBinomial());
    storedParsedName.setCandidatus(pn.isCandidatus());
    storedParsedName.setCultivarEpithet(pn.getCultivarEpithet());
    storedParsedName.setDoubtful(pn.isDoubtful());
    storedParsedName.setGenus(pn.getGenus());
    storedParsedName.setUninomial(pn.getUninomial());
    storedParsedName.setUnparsed(pn.getUnparsed());
    storedParsedName.setTrinomial(pn.isTrinomial());
    storedParsedName.setIncomplete(pn.isIncomplete());
    storedParsedName.setIndetermined(pn.isIndetermined());
    storedParsedName.setTerminalEpithet(pn.getTerminalEpithet());
    storedParsedName.setInfragenericEpithet(pn.getInfragenericEpithet());
    storedParsedName.setInfraspecificEpithet(pn.getInfraspecificEpithet());
    storedParsedName.setExtinct(pn.isExtinct());
    storedParsedName.setPublishedIn(pn.getPublishedIn());
    storedParsedName.setSanctioningAuthor(pn.getSanctioningAuthor());
    storedParsedName.setSpecificEpithet(pn.getSpecificEpithet());
    storedParsedName.setPhrase(pn.getPhrase());
    storedParsedName.setPhraseName(pn.isPhraseName());
    storedParsedName.setVoucher(pn.getVoucher());
    storedParsedName.setNominatingParty(pn.getNominatingParty());
    storedParsedName.setNomenclaturalNote(pn.getNomenclaturalNote());
    storedParsedName.setWarnings(pn.getWarnings());
    if (pn.getBasionymAuthorship() != null) {
      storedParsedName.setBasionymAuthorship(
        StoredParsedName.StoredAuthorship.builder()
          .authors(pn.getBasionymAuthorship().getAuthors())
          .exAuthors(pn.getBasionymAuthorship().getExAuthors())
          .year(pn.getBasionymAuthorship().getYear()).build()
      );
    }
    if (pn.getCombinationAuthorship() != null) {
      storedParsedName.setCombinationAuthorship(
        StoredParsedName.StoredAuthorship.builder()
          .authors(pn.getCombinationAuthorship().getAuthors())
          .exAuthors(pn.getCombinationAuthorship().getExAuthors())
          .year(pn.getCombinationAuthorship().getYear()).build()
      );
    }
    storedParsedName.setType(pn.getType() != null ? pn.getType().name() : null);
    storedParsedName.setNotho(pn.getNotho() != null ? pn.getNotho().name() : null);
    return storedParsedName;
  }
}
