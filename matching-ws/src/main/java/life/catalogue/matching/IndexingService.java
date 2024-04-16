package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.io.CsvWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.api.UnparsableNameException;
import org.gbif.utils.file.csv.CSVReader;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexingService {

  private static Logger LOG = LoggerFactory.getLogger(IndexingService.class);

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

  private static final ScientificNameAnalyzer analyzer = new ScientificNameAnalyzer();

  @Transactional
  public void writeCLBToFile(final Integer datasetKey) throws Exception {

    // I am seeing better results with this MyBatis Pooling DataSource for Cursor queries
    // (parallelism) as opposed to the spring managed DataSource
    PooledDataSource dataSource = new PooledDataSource(clDriver, clbUrl, clbUser, clPassword);

    // Create index writer configuration
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    // Create a session factory
    SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
    sessionFactoryBean.setDataSource(dataSource);
    SqlSessionFactory factory = sessionFactoryBean.getObject();
    assert factory != null;
    factory.getConfiguration().addMapper(IndexingMapper.class);

    LOG.info("Writing dataset to file...");
    final AtomicInteger counter = new AtomicInteger(0);
    final String fileName = exportPath + "/" + datasetKey + "/" + "index.csv";
    FileUtils.forceMkdir(new File(exportPath + "/" + datasetKey));
    try (SqlSession session = factory.openSession(false);
         final CsvWriter writer = new CsvWriter(new FileWriter(fileName))) {

      // Create index writer
      consume(
        () -> session.getMapper(IndexingMapper.class).getAllForDataset(datasetKey),
        name -> {
          try {
            writer.write(new String[]{
              name.id,
              name.parentId,
              name.scientificName,
              name.authorship,
              name.rank,
              name.status,
              name.nomenclaturalCode
            });
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          counter.incrementAndGet();
        });
    }
    // write metadata file in JSON format
    LOG.info("Records written to file {}: {}", fileName, counter.get());
  }

  @Transactional
  public void indexFile(Integer datasetId) throws Exception {

    // Create index directory
    if (new File(indexPath).exists()) {
      FileUtils.forceDelete(new File(indexPath));
    }
    Path indexDirectory = Paths.get(indexPath);
    Directory directory = FSDirectory.open(indexDirectory);

    // Create index writer configuration
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    // Create a session factory
    LOG.info("Indexing dataset...");
    final AtomicInteger counter = new AtomicInteger(0);

    final String filePath = exportPath + "/" + datasetId + "/index.csv";

    // File source, String encoding, String delimiter, Character quotes, Integer headerRows
    try (CSVReader reader = new CSVReader(new File(filePath), "UTF-8", ",",
      '"', 0);
         IndexWriter indexWriter = new IndexWriter(directory, config)) {

      while(reader.hasNext()){
        String[] row = reader.next();
        NameUsage name = new NameUsage();
        name.id = row[0];
        name.parentId = row[1];
        name.scientificName = row[2];
        name.authorship = row[3];
        name.rank = row[4];
        name.status = row[5];
        name.nomenclaturalCode = row[6];
        Document doc = toDoc(name);
        indexWriter.addDocument(doc);
        counter.incrementAndGet();
      }
      indexWriter.commit();
      indexWriter.forceMerge(1);
    }
    // write metadata file in JSON format
    LOG.info("Indexed: {}", counter.get());
  }

  @Transactional
  public void runDatasetIndexing(final Integer datasetKey) throws Exception {

    // I am seeing better results with this MyBatis Pooling DataSource for Cursor queries
    // (parallelism) as opposed to the spring managed DataSource
    PooledDataSource dataSource = new PooledDataSource(clDriver, clbUrl, clbUser, clPassword);

    // Create index directory
    if (new File(indexPath).exists()) {
      FileUtils.forceDelete(new File(indexPath));
    }
    Path indexDirectory = Paths.get(indexPath);
    Directory directory = FSDirectory.open(indexDirectory);

    // Create index writer configuration
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
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
              Document doc = toDoc(name);
              indexWriter.addDocument(doc);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            counter.incrementAndGet();
          });
      indexWriter.commit();
      indexWriter.forceMerge(1);
    }
    // write metadata file in JSON format
    LOG.info("Indexed: {}", counter.get());
  }

  private static Document toDoc(NameUsage nameUsage) {

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
      String canonical = org.gbif.nameparser.util.NameFormatter.canonicalMinimal(pn);
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
    // FIXME Re-check this understanding. If the name is a synonym, then parentId name usage points
    // to the accepted name
    if (nameUsage.status.equals(TaxonomicStatus.SYNONYM.name())) {
      doc.add(new StringField(FIELD_ACCEPTED_ID, nameUsage.parentId, Field.Store.YES));
    }

    // analyzed name field - this is what we search upon
    doc.add(new TextField(FIELD_CANONICAL_NAME, canonical, Field.Store.YES));

    // store full name and classification only to return a full match object for hits
    String nameComplete = nameUsage.scientificName;
    if (StringUtils.isNotBlank(nameUsage.authorship)) {
      nameComplete += " " + nameUsage.authorship;
    }
    doc.add(new StringField(FIELD_SCIENTIFIC_NAME, nameComplete, Field.Store.YES));

    // this lucene index is not persistent, so not risk in changing ordinal numbers
    doc.add(new StringField(FIELD_RANK, nameUsage.rank, Field.Store.YES));

    if (nameUsage.parentId != null) {
      doc.add(new StringField(FIELD_PARENT_ID, nameUsage.parentId, Field.Store.YES));
    }

    // FIXME: old implementation allowed only 3 values for status: accepted, doubtful and synonym
    // Shall we pass more values and re-educate pipelines ?
    //    if (nameUsage.status == null) {
    //      status = TaxonomicStatus.DOUBTFUL;
    //    } else if (status.isSynonym()) {
    //      status = TaxonomicStatus.SYNONYM;
    //    }
    //    doc.add(new StoredField(FIELD_STATUS, status.ordinal()));
    doc.add(new StringField(FIELD_STATUS, nameUsage.status, Field.Store.YES));

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
