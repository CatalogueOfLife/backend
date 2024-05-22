package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;

import au.com.bytecode.opencsv.CSVReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import au.com.bytecode.opencsv.CSVWriter;
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
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.ws.rs.NotFoundException;

/**
 * Service to index a dataset from the Checklist Bank.
 */
@Service
public class IndexingService {

  private static final Logger LOG = LoggerFactory.getLogger(IndexingService.class);

  @Value("${index.path:/tmp/matching-index}")
  String indexPath;

  @Value("${export.path:/tmp/matching-export}")
  String exportPath;

  @Value("${clb.url}")
  String clbUrl;

  @Value("${clb.user}")
  String clbUser;

  @Value("${clb.password}")
  String clPassword;

  @Value("${clb.driver}")
  String clDriver;

  private static final String REL_PATTERN_STR = "(\\d+)(?:LX?RC?|R(\\d+))";
  private static final Pattern REL_PATTERN = Pattern.compile("^" + REL_PATTERN_STR + "$");

  protected static final ScientificNameAnalyzer analyzer = new ScientificNameAnalyzer();

  protected static IndexWriterConfig getIndexWriterConfig() {
    Map<String, Analyzer> analyzerPerField = new HashMap<>();
    analyzerPerField.put(FIELD_SCIENTIFIC_NAME, new StandardAnalyzer());
    PerFieldAnalyzerWrapper aWrapper = new PerFieldAnalyzerWrapper(analyzer, analyzerPerField);
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

  @Transactional
  public void writeCLBToFile(@NotNull final String datasetKeyInput) throws Exception {

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
        final CSVWriter writer = new CSVWriter(new FileWriter(fileName), '$')) {
      // Create index writer
      consume(
          () -> session.getMapper(IndexingMapper.class).getAllForDataset(validDatasetKey),
          name -> {
            try {
              writer.writeNext(
                  new String[] {
                    name.id,
                    name.parentId,
                    name.scientificName,
                    name.authorship,
                    name.rank,
                    name.status,
                    name.nomenclaturalCode,
                    name.sourceId,
                    name.sourceDatasetKey,
                    name.parentSourceId,
                    name.parentSourceDatasetKey
                  });
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            counter.incrementAndGet();
          });
    } finally {
      dataSource.forceCloseAll();
    }

    // write metadata file in JSON format
    LOG.info("Records written to file {}: {}", fileName, counter.get());
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
        counter++;
      }
    }
    writer.close();
    LOG.info("Finished building nub index with {} usages", counter);
    return dir;
  }

  private static IndexWriter getIndexWriter(Directory dir) throws IOException {
    return new IndexWriter(dir, getIndexWriterConfig());
  }

  @Transactional
  public void indexFile(String exportPath, String indexPath) throws Exception {

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

    try (CSVReader reader = new CSVReader(new FileReader(filePath), '$', '"');
        IndexWriter indexWriter = new IndexWriter(directory, config)) {

      String[] row = reader.readNext();
      while (row != null) {
        if (row.length != 11) {
          LOG.warn("Skipping row with invalid number of columns: {}", String.join(",", row));
          row = reader.readNext();
          continue;
        }
        if (counter.get() % 100000 == 0) {
          LOG.info("Indexed: {} taxa", counter.get());
        }

        NameUsage nameUsage =
            NameUsage.builder()
                .id(row[0])
                .parentId(row[1])
                .scientificName(row[2])
                .authorship(row[3])
                .rank(row[4])
                .status(row[5])
                .nomenclaturalCode(row[6])
                .sourceId(row[7])
                .sourceDatasetKey(row[8])
                .parentSourceId(row[9])
                .parentSourceDatasetKey(row[10])
                .build();
        Document doc = toDoc(nameUsage);
        indexWriter.addDocument(doc);
        counter.incrementAndGet();
        row = reader.readNext();
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
    Rank rank = Rank.valueOf(nameUsage.rank);

    Optional<String> optCanonical = Optional.empty();
    try {
      NomCode nomCode = null;
      if (!StringUtils.isEmpty(nameUsage.nomenclaturalCode)) {
        nomCode = NomCode.valueOf(nameUsage.nomenclaturalCode);
      }
      ParsedName pn = NameParsers.INSTANCE.parse(nameUsage.scientificName, rank, nomCode);

      // canonicalMinimal will construct the name without the hybrid marker and authorship
      String canonical = NameFormatter.canonicalMinimal(pn);
      optCanonical = Optional.ofNullable(canonical);
    } catch (UnparsableNameException | InterruptedException e) {
      // do nothing
      LOG.debug("Unable to parse name to create canonical: {}", nameUsage.scientificName);
    }

    final String canonical = optCanonical.orElse(nameUsage.scientificName);

    // use custom precision step as we do not need range queries and prefer to save memory usage
    // instead
    doc.add(new StringField(FIELD_ID, nameUsage.id, Field.Store.YES));

    // we only store accepted key, no need to index it
    // If the name is a synonym, then parentId name usage points
    // to the accepted name
    if (nameUsage.status != null
        && nameUsage.status.equals(TaxonomicStatus.SYNONYM.name())
        && nameUsage.parentId != null) {
      doc.add(new StringField(FIELD_ACCEPTED_ID, nameUsage.parentId, Field.Store.YES));
    }

    // analyzed name field - this is what we search upon
    doc.add(new TextField(FIELD_CANONICAL_NAME, canonical, Field.Store.YES));

    // store full name and classification only to return a full match object for hits
    String nameComplete = nameUsage.scientificName;
    if (StringUtils.isNotBlank(nameUsage.authorship)) {
      nameComplete += " " + nameUsage.authorship;
    }
    doc.add(new TextField(FIELD_SCIENTIFIC_NAME, nameComplete, Field.Store.YES));

    // this lucene index is not persistent, so not risk in changing ordinal numbers
    doc.add(new StringField(FIELD_RANK, nameUsage.rank, Field.Store.YES));

    if (nameUsage.parentId != null && !nameUsage.parentId.equals(nameUsage.id)) {
      doc.add(new StringField(FIELD_PARENT_ID, nameUsage.parentId, Field.Store.YES));
    }

    if (nameUsage.status != null) {
      doc.add(new StringField(FIELD_STATUS, nameUsage.status, Field.Store.YES));
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
