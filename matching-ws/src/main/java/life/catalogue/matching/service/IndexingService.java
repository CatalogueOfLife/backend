package life.catalogue.matching.service;

import static life.catalogue.matching.util.IndexConstants.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ReleaseAttempt;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxonomicStatus;

import life.catalogue.matching.db.DatasetMapper;
import life.catalogue.matching.db.IndexingMapper;

import life.catalogue.matching.index.ScientificNameAnalyzer;
import life.catalogue.matching.model.Classification;
import life.catalogue.matching.model.Dataset;
import life.catalogue.matching.model.NameUsage;

import life.catalogue.matching.model.NameUsageMatch;

import life.catalogue.matching.util.NameParsers;

import lombok.extern.slf4j.Slf4j;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  protected final MatchingService matchingService;

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
   * Writes an export of  the name usages in a checklist bank dataset to a CSV file.
   *
   * @param datasetKeyInput a dataset key or a release key
   * @throws Exception if the dataset key is invalid or the export fails
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
    Optional<Dataset> dataset = lookupDataset(factory, datasetKeyInput);
    if (dataset.isEmpty()) {
      throw new IllegalArgumentException("Invalid dataset key: " + datasetKeyInput);
    }

    final Integer validDatasetKey = dataset.get().getKey();
    final String directory = exportPath + "/" + datasetKeyInput;
    final String fileName = directory + "/" + INDEX_CSV;
    final String metadata = directory + "/" + METADATA_JSON;

    log.info("Writing dataset to file {}", fileName);
    final AtomicInteger counter = new AtomicInteger(0);

    FileUtils.forceMkdir(new File(directory));
    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(new File(metadata), dataset.get());

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
              sbc.write(cleanNameUsage(name));
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            counter.incrementAndGet();
          });
    } finally {
      dataSource.forceCloseAll();
    }

    // write metadata file in JSON format
    log.info("ChecklistBank export written to file {}: {}", fileName, counter.get());
  }

  private NameUsage cleanNameUsage(NameUsage name) {
    if (name.getRank() == null) {
      name.setRank(Rank.UNRANKED.toString());
    }
    name.setScientificName(name.getScientificName().replaceAll("[/\\\\]", "").trim());
    return name;
  }

  @Transactional
  public void indexIdentifiers(String datasetKey) throws Exception {
    writeCLBToFile(datasetKey);
    indexFile(exportPath  + "/" + datasetKey, tempIndexPath + "/" + datasetKey);
    writeJoinIndex( tempIndexPath + "/"  + datasetKey, indexPath + "/" + IDENTIFIERS_DIR + "/" + datasetKey, false);
  }

  @Transactional
  public void indexIUCN(String datasetKey) throws Exception {
    writeCLBIUCNToFile(datasetKey);
    indexFile(exportPath  + "/" + datasetKey, tempIndexPath + "/" + datasetKey);
    writeJoinIndex( tempIndexPath + "/" + datasetKey, indexPath + "/" + ANCILLARY_DIR + "/" + datasetKey, true);
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
    Optional<Dataset> dataset =  lookupDataset(factory, datasetKeyInput);
    if (dataset.isEmpty()) {
      throw new IllegalArgumentException("Invalid dataset key: " + datasetKeyInput);
    }

    log.info("Writing dataset to file...");
    final AtomicInteger counter = new AtomicInteger(0);
    final String fileName = exportPath + "/" + datasetKeyInput + "/" + INDEX_CSV;
    final String metadata = exportPath + "/" + datasetKeyInput  + "/" + METADATA_JSON;

    FileUtils.forceMkdir(new File(exportPath + "/" + datasetKeyInput));

    // write metadata to file
    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(new File(metadata), dataset.get());

    try (SqlSession session = factory.openSession(false);
         final ICSVWriter writer = new CSVWriterBuilder(new FileWriter(fileName)).withSeparator('$').build()) {

      final ObjectMapper objectMapper = new ObjectMapper();
      final StatefulBeanToCsv<NameUsage> sbc = new StatefulBeanToCsvBuilder<NameUsage>(writer)
        .withQuotechar('\'')
        .build();

      // Create index writer
      consume(
        () -> session.getMapper(IndexingMapper.class).getAllWithExtensionForDataset(dataset.get().getKey()),
        nameUsage -> {
          try {
            if (StringUtils.isNotBlank(nameUsage.getExtension())){
              // parse it
              JsonNode node = objectMapper.readTree(nameUsage.getExtension());
              nameUsage.setCategory(node.path(IUCN_THREAT_STATUS).asText());
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
    log.info("ChecklistBank IUCN export written to file {}: {}", fileName, counter.get());
  }

  public static Directory newMemoryIndex(Iterable<NameUsage> usages) throws IOException {
    log.info("Start building a new RAM index");
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

          // match to main dataset
          try {
            // use strict matching for classification to classification matching
            NameUsageMatch nameUsageMatch = matchingService.match(scientificName, classification, true);
            if (nameUsageMatch.getUsage() != null) {
              doc.add(new StringField(FIELD_JOIN_ID,
                nameUsageMatch.getAcceptedUsage() != null ? nameUsageMatch.getAcceptedUsage().getKey() :
                  nameUsageMatch.getUsage().getKey(), Field.Store.YES)
              );
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
        e.printStackTrace();
      }
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
  public void createMainIndexFromFile(String exportPath, String indexPath) throws Exception {
    indexFile(exportPath, indexPath + "/" + MAIN_INDEX_DIR);
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

      log.info("Final index commit");
      indexWriter.commit();
      log.info("Optimising index....");
      indexWriter.forceMerge(1);
      log.info("Optimisation complete.");
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

  class YourThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      return new Thread(r, "NameUsage-Indexing-taskThread");
    }
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
        e.printStackTrace();
      }
    }
  }

  @Transactional
  public void runDatasetIndexing(final Integer datasetKey) throws Exception {

    // I am seeing better results with this MyBatis Pooling DataSource for Cursor queries
    // (parallelism) as opposed to the spring managed DataSource
    PooledDataSource dataSource = new PooledDataSource(clDriver, clbUrl, clbUser, clPassword);

    // Create index directory
    Path indexDirectory = initialiseIndexDirectory(indexPath);
    Directory directory = FSDirectory.open(indexDirectory);

    // Create a session factory
    SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
    sessionFactoryBean.setDataSource(dataSource);
    SqlSessionFactory factory = sessionFactoryBean.getObject();
    assert factory != null;
    factory.getConfiguration().addMapper(IndexingMapper.class);

    // Create index writer configuration
    IndexWriterConfig config = getIndexWriterConfig();
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);


    log.info("Indexing dataset...");
    final AtomicInteger counter = new AtomicInteger(0);
    try (SqlSession session = factory.openSession(false);
        IndexWriter indexWriter = new IndexWriter(directory, config)) {

      // Create index writer
      consume(
          () -> session.getMapper(IndexingMapper.class).getAllForDataset(datasetKey),
          name -> {
            try {
              if (counter.get() % 10000 == 0) {
                log.info("Indexed: {} taxa", counter.get());
              }
              Document doc = toDoc(name);
              indexWriter.addDocument(doc);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            counter.incrementAndGet();
          });
      log.info("Final index commit");
      indexWriter.commit();
      log.info("Optimising index....");
      indexWriter.forceMerge(1);
      log.info("Optimisation complete.");
    }
    // write metadata file in JSON format
    log.info("Indexed: {}", counter.get());
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
      log.debug("Unable to parse name to create canonical: {}", nameUsage.getScientificName());
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
