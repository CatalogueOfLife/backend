package life.catalogue.matching.service;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ReleaseAttempt;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.db.DatasetMapper;
import life.catalogue.matching.index.ScientificNameAnalyzer;
import life.catalogue.matching.model.*;
import life.catalogue.matching.util.IOUtil;
import life.catalogue.matching.util.NameParsers;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.api.UnparsableNameException;
import org.gbif.nameparser.util.NameFormatter;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
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
import org.apache.lucene.util.BytesRef;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

import static life.catalogue.matching.util.IndexConstants.*;

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

  @Value("${clb.url:jdbc:postgresql://localhost:5432/clb}")
  String clbUrl;

  @Value("${clb.user:clb}")
  String clbUser;

  @Value("${clb.password:not-set}")
  String clPassword;

  @Value("${indexing.threads:6}")
  Integer indexingThreads;

  @Value("${indexing.batchsize:10000}")
  Integer indexingBatchSize;

  @Value("${indexing.fetchsize:50000}")
  Integer dbFetchSize;

  protected final IOUtil ioUtil;

  protected final MatchingService matchingService;

  private static final String REL_PATTERN_STR = "(\\d+)(?:LX?RC?|R(\\d+))";
  private static final Pattern REL_PATTERN = Pattern.compile("^" + REL_PATTERN_STR + "$");

  protected static final ScientificNameAnalyzer scientificNameAnalyzer = new ScientificNameAnalyzer();

  public IndexingService(MatchingService matchingService, IOUtil ioUtil) {
    this.matchingService = matchingService;
    this.ioUtil = ioUtil;
  }

  protected static IndexWriterConfig getIndexWriterConfig() {
    Map<String, Analyzer> analyzerPerField = new HashMap<>();
    analyzerPerField.put(FIELD_SCIENTIFIC_NAME, new StandardAnalyzer());
    analyzerPerField.put(FIELD_CANONICAL_NAME, scientificNameAnalyzer);
    PerFieldAnalyzerWrapper aWrapper = new PerFieldAnalyzerWrapper( new KeywordAnalyzer(), analyzerPerField);
    IndexWriterConfig config = new IndexWriterConfig(aWrapper);
    config.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    config.setRAMBufferSizeMB(256.0);
    return config;
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
    } catch (NumberFormatException ignored) {
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
        "n.code as code, " +
        "n.type as type, " +
        "n.genus as genericName, " +
        "n.infrageneric_epithet as infragenericEpithet, " +
        "n.specific_epithet as specificEpithet, " +
        "n.infraspecific_epithet as infraspecificEpithet, " +
        "'' as extension, " +
        "'' as category " +
        "FROM name_usage nu " +
        "INNER JOIN " +
        "name n on n.id = nu.name_id AND n.dataset_key = " + dataset.get().getClbKey() +
        " WHERE " +
        "nu.dataset_key = " + dataset.get().getClbKey();

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

            NameUsage name = NameUsage.builder()
              .id(rs.getString("id"))
              .parentId(rs.getString("parentId"))
              .scientificName(rs.getString("scientificName"))
              .authorship(rs.getString("authorship"))
              .status(rs.getString("status"))
              .rank(rs.getString("rank"))
              .code(rs.getString("code"))
              .type(rs.getString("type"))
              .genericName(rs.getString("genericName"))
              .infragenericEpithet(rs.getString("infragenericEpithet"))
              .specificEpithet(rs.getString("specificEpithet"))
              .infraspecificEpithet(rs.getString("infraspecificEpithet"))
              .category(rs.getString("category"))
              .extension(rs.getString("extension"))
              .build();

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
    return Files.exists(metadataPath) && Files.size(metadataPath) != 0;
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
        "n.code as code, " +
        "v.terms as extension, " +
        "'' as category " +
        "FROM " +
        "name_usage nu " +
        " INNER JOIN " +
        "name n on n.id = nu.name_id AND n.dataset_key = " + dataset.get().getClbKey() +
        " LEFT JOIN " +
        "distribution d on d.taxon_id = nu.id AND d.dataset_key = " + dataset.get().getClbKey() +
        " LEFT JOIN " +
        "verbatim v on v.id = d.verbatim_key AND v.dataset_key = " + dataset.get().getClbKey() +
        " WHERE " +
        "nu.dataset_key = " + dataset.get().getClbKey();

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
            NameUsage nameUsage = NameUsage.builder()
              .id(rs.getString("id"))
              .parentId(rs.getString("parentId"))
              .scientificName(rs.getString("scientificName"))
              .authorship(rs.getString("authorship"))
              .status(rs.getString("status"))
              .rank(rs.getString("rank"))
              .code(rs.getString("code"))
              .category(rs.getString("category"))
              .extension(rs.getString("extension"))
              .build();
            try {
              if (StringUtils.isNotBlank(nameUsage.getExtension())) {
                // parse json to retrieve IUCN threat status
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

  /**
   * Indexes a set of name usages into a new Lucene index in memory.
   * Only used for testing purposes.
   *
   * @param usages the name usages to index
   * @throws IOException if an error occurs during indexing
   */
  public static Directory newMemoryIndex(Iterable<NameUsage>... usages) throws IOException {
    log.info("Start building a new RAM index");
    Directory tempDir = new ByteBuffersDirectory();
    IndexWriter writer = getIndexWriter(tempDir);

    Directory tempNestedDir = new ByteBuffersDirectory();
    IndexWriter nestedWriter = getIndexWriter(tempNestedDir);

    // creates initial index segments
    writer.commit();
    for (var iter : usages) {
      for (NameUsage u : iter) {
        if (u != null && u.getId() != null) {
          writer.addDocument(toDoc(u));
        }
      }
    }
    writer.close();
    nestedWriter.close();

    // de-normalise
    IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(tempDir));
    IndexSearcher nestedSearcher = new IndexSearcher(DirectoryReader.open(tempNestedDir));
    TopDocs results = searcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);
    ScoreDoc[] hits = results.scoreDocs;

    Directory denormedDir = new ByteBuffersDirectory();
    IndexWriter denormedWriter = getIndexWriter(denormedDir);
    IOUtil ioUtil = new IOUtil();

    List<Document> batch = new ArrayList<>();
    for (ScoreDoc hit : hits) {
      batch.add(searcher.storedFields().document(hit.doc));
    }
    new DenormIndexTask(searcher, nestedSearcher, denormedWriter, ioUtil, batch).run();

    // optimize the index
    denormedWriter.flush();
    denormedWriter.commit();
    denormedWriter.close();

    return denormedDir;
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
      Long[] counters = createJoinIndex(matchingService, tempDirectory, ancillaryDirectory, acceptedOnly, true, indexingThreads, indexingBatchSize);

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
   * @return an array containing the number of documents indexed and the number of documents matched to the main index
   * @throws IOException if the index cannot be created
   */
  public static Long[] createJoinIndex(MatchingService matchingService,
                                       Directory tempUsageIndexDirectory,
                                       Directory outputDirectory,
                                       boolean acceptedOnly,
                                       boolean closeDirectoryOnExit,
                                       int indexingThreads,
                                       int indexingBatchSize)
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

      if (batch.size() >= indexingBatchSize) {
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

  static class DenormIndexTask implements Runnable {
    private final IndexWriter writer;
    private final List<Document> docs;
    private final IndexSearcher searcher;
    private final IndexSearcher nestedSearcher;
    private final IOUtil ioUtil;

    public DenormIndexTask(IndexSearcher searcher, IndexSearcher nestedSearcher, IndexWriter writer, IOUtil ioUtil, List<Document> docs) {
      this.searcher = searcher;
      this.nestedSearcher = nestedSearcher;
      this.writer = writer;
      this.ioUtil = ioUtil;
      this.docs = docs;
    }

    @Override
    public void run() {
      try {
        for (Document doc : docs) {
          // set the higher classification
          StoredClassification storedClassification = loadHierarchyWithIDs(searcher, doc);

          addNestedSetIDs(nestedSearcher, doc);

          ioUtil.serialiseField(doc, FIELD_CLASSIFICATION, storedClassification);
          writer.addDocument(doc);
        }
        writer.flush();
      } catch (Exception e) {
        log.error("Error writing documents to " + ANCILLARY_DIR + " index: {}", e.getMessage(), e);
      }
    }

    private void addNestedSetIDs(IndexSearcher nestedSearcher, Document doc) {
      try {

        String id = doc.get(FIELD_ID);
        String parentID = doc.get(FIELD_PARENT_ID);
        String status = doc.get(FIELD_STATUS);
        String idToUse = id;

        // if the status is not accepted, use the parent ID (which is the accepted ID)
        if (status != null && !status.equals(TaxonomicStatus.ACCEPTED.name()) ) {
          idToUse = parentID;
        }

        if (idToUse != null) {
          TopDocs topDocs = nestedSearcher.search(new TermQuery(new Term(FIELD_ID, idToUse)), 1);
          if (topDocs.totalHits.value > 0) {
            Document nestedDoc = nestedSearcher.doc(topDocs.scoreDocs[0].doc);
            doc.add(new LongField(FIELD_LEFT_NESTED_SET_ID, Long.parseLong(nestedDoc.get(FIELD_LEFT_NESTED_SET_ID)), Field.Store.YES));
            doc.add(new LongField(FIELD_RIGHT_NESTED_SET_ID, Long.parseLong(nestedDoc.get(FIELD_RIGHT_NESTED_SET_ID)), Field.Store.YES));
          }
        } else {
          log.error("Taxon ID {} has status {}, with parentID {}", id, status, parentID);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
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

          String id = doc.get(FIELD_ID);
          var rank = Rank.valueOf(doc.get(FIELD_RANK));
          Map<String, String> hierarchy = loadHierarchy(searcher, id);
          String scientificName = doc.get(FIELD_SCIENTIFIC_NAME);
          String status = doc.get(FIELD_STATUS);
          if (acceptedOnly && !isAccepted(status)) {
            // skip synonyms, otherwise we would index them twice
            continue;
          }

          ClassificationQuery classification = new ClassificationQuery();
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
            NameUsageMatch nameUsageMatch = matchingService.match(scientificName, rank, classification, true);
            if (nameUsageMatch.getUsage() != null && nameUsageMatch.getDiagnostics().getMatchType() == MatchType.HIGHERRANK) {
              log.info("Ignore higher match for {} {} # {}", rank, scientificName, id);
            } else if (nameUsageMatch.getUsage() != null) {
              doc.add(new StringField(FIELD_JOIN_ID,
                nameUsageMatch.getAcceptedUsage() != null ? nameUsageMatch.getAcceptedUsage().getKey() :
                  nameUsageMatch.getUsage().getKey(), Field.Store.YES)
              );

              writer.addDocument(doc);
              matchedCounter.incrementAndGet();
            } else {
              log.info("No match for {} {} # {}", rank, scientificName, id);
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

  private static Map<String, String> loadHierarchy(IndexSearcher searcher, String id) {
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

  public static StoredClassification loadHierarchyWithIDs(IndexSearcher searcher, Document leafDoc) {

    // get leaf node
    StoredClassification.StoredClassificationBuilder builder = StoredClassification.builder();

    ArrayDeque<StoredName> classification = new ArrayDeque<>();
    String id = leafDoc.get(FIELD_PARENT_ID);
    String acceptedId = leafDoc.get(FIELD_ACCEPTED_ID);
    if (acceptedId != null){
      id = acceptedId;
    }

    // get leaf node
    while (id != null) {
      Optional<Document> docOpt = getById(searcher, id);
      if (docOpt.isEmpty()) {
        break;
      }
      Document doc = docOpt.get();
      classification.addFirst(StoredName.builder()
        .key(doc.get(FIELD_ID))
        .rank(doc.get(FIELD_RANK))
        .name(doc.get(FIELD_CANONICAL_NAME))
        .build()
      );

      // for next step
      id = doc.get(FIELD_PARENT_ID);
    }

    // add the leaf node
    if (TaxonomicStatus.ACCEPTED.name().equalsIgnoreCase(leafDoc.get(FIELD_STATUS))
        || TaxonomicStatus.PROVISIONALLY_ACCEPTED.name().equalsIgnoreCase(leafDoc.get(FIELD_STATUS))
    ) {
      classification.addLast(StoredName.builder()
        .key(leafDoc.get(FIELD_ID))
        .rank(leafDoc.get(FIELD_RANK))
        .name(leafDoc.get(FIELD_CANONICAL_NAME))
        .build()
      );
    }

    return builder.names(new ArrayList<>(classification)).build();
  }

  @Transactional
  public void createMainIndex(String datasetKey) throws Exception {
    final String mainIndexPath = indexPath + "/" + MAIN_INDEX_DIR;
    final String tempDenormedPath = indexPath + "/denormed";
    if (indexExists(mainIndexPath)){
      log.info("Main index already exists at path {}", mainIndexPath);
      return;
    }
    log.info("Generating index for path {}", mainIndexPath);
    writeCLBToFile(datasetKey);
    indexFile(exportPath + "/" + datasetKey, mainIndexPath);
    denormalizeMainIndex(mainIndexPath, tempDenormedPath);
  }

  private void nestedSetMainIndex(String mainIndexPath, String nestedTempPath) throws IOException {

    StopWatch watch = new StopWatch();
    watch.start();
    log.info("Generating nested set index for main index...");
    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
    Directory mainDirectory = FSDirectory.open(Paths.get(mainIndexPath));
    IndexReader tempReader = DirectoryReader.open(mainDirectory);
    IndexWriter nestedIndexWriter = new IndexWriter(FSDirectory.open(Paths.get(nestedTempPath)), getIndexWriterConfig());

    IndexSearcher searcher = new IndexSearcher(tempReader);
    Query query = new BooleanQuery.Builder()
      .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
      .add(new FieldExistsQuery(FIELD_PARENT_ID), BooleanClause.Occur.MUST_NOT).build();

    // Execute search
    TopDocs topDocs = searcher.search(query, 100);
    long currentIdx = 1;
    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
      long left = currentIdx;
      Document rootTaxon = searcher.doc(scoreDoc.doc);
      if (!rootTaxon.get(FIELD_CANONICAL_NAME).equalsIgnoreCase("incertae sedis")){
        log.info("[Nested set] Starting Root taxon: " + rootTaxon.get(FIELD_CANONICAL_NAME));
        currentIdx = nestedSetTaxon(rootTaxon, searcher, nestedIndexWriter, currentIdx, new ArrayList<>(), 0);
        log.info("[Nested set] Root taxon: " + rootTaxon.get(FIELD_CANONICAL_NAME) + " left: " + left + " right: " + currentIdx);
      }
    }

    // optimize the index
    finishIndex(nestedIndexWriter);
    nestedIndexWriter.commit();
    nestedIndexWriter.close();
    log.info("Nested set index generated.");
  }

  private long nestedSetTaxon(Document doc, IndexSearcher searcher, IndexWriter indexWriter, long currentIndex, List<String> idTracking, int currentDepth) throws IOException {

    String id = doc.get(FIELD_ID);

    if (currentDepth >= 50){
      log.error("Depth {} to great for taxon {}", currentDepth, id);
      return currentIndex;
    }

    String status = doc.get(FIELD_STATUS);

    // if it is a synonym, we avoid recursively looking at child nodes
    if (status != null && !status.equalsIgnoreCase(TaxonomicStatus.ACCEPTED.name())) {
      currentIndex ++;
      Document nestedSet = new Document();
      nestedSet.add(new StringField(FIELD_ID, id, Field.Store.YES));
      nestedSet.add(new LongField(FIELD_RIGHT_NESTED_SET_ID, currentIndex, Field.Store.YES));
      nestedSet.add(new LongField(FIELD_LEFT_NESTED_SET_ID, currentIndex, Field.Store.YES));
      indexWriter.addDocument(nestedSet);
      return currentIndex;
    }

    // find children
    Query query = new BooleanQuery.Builder()
      .add(new TermQuery(new Term(FIELD_PARENT_ID, id)), BooleanClause.Occur.MUST).build();

    TopDocs topDocs = searcher.search(query, 5000);

    // store the left index
    long left = currentIndex;

    List<Document> children = new ArrayList<>();

    // for each child, increment the index and recurse
    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
      Document childDoc = searcher.storedFields().document(scoreDoc.doc);
      if (!childDoc.get(FIELD_CANONICAL_NAME).equalsIgnoreCase("incertae sedis")
        && childDoc.get(FIELD_STATUS).equalsIgnoreCase(TaxonomicStatus.ACCEPTED.name())
      ) {
        currentIndex++;
        children.add(childDoc);
      }
    }

    // sort before minting ids
    for (Document child : children.stream().sorted(Comparator.comparing(d -> d.get(FIELD_CANONICAL_NAME))).collect(Collectors.toList())) {
      currentIndex = nestedSetTaxon(child, searcher, indexWriter, currentIndex, idTracking, currentDepth + 1);
    }

    long right = currentIndex;

    // add this node to the index
    Document nestedSet = new Document();
    nestedSet.add(new StringField(FIELD_ID, id, Field.Store.YES));
    nestedSet.add(new LongField(FIELD_RIGHT_NESTED_SET_ID, right, Field.Store.YES));
    nestedSet.add(new LongField(FIELD_LEFT_NESTED_SET_ID, left, Field.Store.YES));
    indexWriter.addDocument(nestedSet);
    return right;
  }

  private void denormalizeMainIndex(String mainIndexPath, String denormedTempPath) throws IOException {
    //generate nested set index
    nestedSetMainIndex(indexPath + "/" + MAIN_INDEX_DIR, indexPath + "/nested");

    StopWatch watch = new StopWatch();
    watch.start();
    log.info("De-normalizing main index...");
    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
    Directory mainDirectory = FSDirectory.open(Paths.get(mainIndexPath));
    IndexReader tempReader = DirectoryReader.open(mainDirectory);
    IndexReader nestedSetReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath + "/nested")));

    IndexWriter denormIndexWriter = new IndexWriter(FSDirectory.open(
      Paths.get(denormedTempPath)), getIndexWriterConfig());

    IndexSearcher searcher = new IndexSearcher(tempReader);
    IndexSearcher nestedSearcher = new IndexSearcher(nestedSetReader);
    TopDocs results = searcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);
    ScoreDoc[] hits = results.scoreDocs;

    AtomicLong counter = new AtomicLong(0);

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

      if (batch.size() >= 1000) {
        log.info("De-normalisation - starting batch: {} taxa", counter.get());
        List<Document> finalBatch = batch;
        exec.submit(new DenormIndexTask(searcher, nestedSearcher, denormIndexWriter, ioUtil, finalBatch));
        batch = new ArrayList<>();
      }
    }

    //final batch
    exec.submit(new DenormIndexTask(searcher, nestedSearcher, denormIndexWriter, ioUtil, batch));

    log.info("Finished reading main index file. Finishing denormalisation of index...");

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
    mainDirectory.close();

    // optimize the index
    log.info("Disk size before optimisation: {}", FileUtils.sizeOfDirectory(new File(denormedTempPath)));
    finishIndex(denormIndexWriter);
    denormIndexWriter.commit();
    log.info("Disk size after optimisation: {}", FileUtils.sizeOfDirectory(new File(denormedTempPath)));

    // remove old index, and move new one into place
    FileUtils.copyFile(new File(mainIndexPath + "/" + METADATA_JSON), new File(denormedTempPath + "/" + METADATA_JSON));
    FileUtils.deleteDirectory(new File(mainIndexPath));
    FileUtils.moveDirectory(new File(denormedTempPath), new File(mainIndexPath));
    FileUtils.deleteDirectory(new File(indexPath + "/nested"));

    watch.stop();
    log.info("De-normalisation complete: {} documents written, time taken {} mins", counter.get(), watch.getTime(TimeUnit.MINUTES));
  }

  private void indexFile(String exportPath, String indexPath) throws Exception {

    // Create index directory
    Path indexDirectory = initialiseIndexDirectory(indexPath);
    Directory directory = FSDirectory.open(indexDirectory);

    // Create index writer configuration
    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    // Create a session factory

    final AtomicLong counter = new AtomicLong(0);
    final String filePath = exportPath + "/" + INDEX_CSV;
    final String metadataPath = exportPath + "/" + METADATA_JSON;

    log.info("Indexing dataset from CSV {} to {}", filePath, indexPath);

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
        if (batch.size() >= indexingBatchSize) {
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
      } catch (Exception e) {
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
    Rank rank = Rank.valueOf(nameUsage.getRank().toUpperCase());

    Optional<String> optCanonical = Optional.empty();
    ParsedName pn = null;
    NomCode nomCode = null;
    try {
      if (!StringUtils.isEmpty(nameUsage.getCode())) {
        nomCode = NomCode.valueOf(nameUsage.getCode());
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
        doc.add(new StringField(FIELD_FORMATTED, NameFormatter.canonicalCompleteHtml(pn), Field.Store.YES));
      } catch (Exception e) {
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
    doc.add(new SortedDocValuesField(FIELD_CANONICAL_NAME, new BytesRef(canonical)));

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
      doc.add(new SortedDocValuesField(FIELD_PARENT_ID, new BytesRef(nameUsage.getParentId())));
    }

    if (StringUtils.isNotBlank(nameUsage.getCode())) {
      doc.add(new StringField(FIELD_NOMENCLATURAL_CODE, nameUsage.getCode(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getType())) {
      doc.add(new StringField(FIELD_TYPE, nameUsage.getType(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getGenericName())) {
      doc.add(new StringField(FIELD_GENERICNAME, nameUsage.getGenericName(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getInfragenericEpithet())) {
      doc.add(new StringField(FIELD_INFRAGENERIC_EPITHET, nameUsage.getInfragenericEpithet(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getSpecificEpithet())) {
      doc.add(new StringField(FIELD_SPECIFIC_EPITHET, nameUsage.getSpecificEpithet(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getInfraspecificEpithet())) {
      doc.add(new StringField(FIELD_INFRASPECIFIC_EPITHET, nameUsage.getInfraspecificEpithet(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getStatus())) {
      doc.add(new StringField(FIELD_STATUS, nameUsage.getStatus(), Field.Store.YES));
    }

    if (StringUtils.isNotBlank(nameUsage.getCategory())) {
      doc.add(new StringField(FIELD_CATEGORY, nameUsage.getCategory(), Field.Store.YES));
    }

    return doc;
  }
}
