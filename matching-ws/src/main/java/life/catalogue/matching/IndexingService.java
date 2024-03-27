package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.api.UnparsableNameException;
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

  @Value("${spring.datasource.url}")
  String clbUrl;

  @Value("${clb.user}")
  String clbUser;

  @Value("${clb.password}")
  String clPassword;

  @Value("${clb.driver}")
  String clDriver;

  private static final ScientificNameAnalyzer analyzer = new ScientificNameAnalyzer();

  @Transactional
  public void runDatasetIndexing(String datasetId) throws Exception {

    // FIXME I am seeing better results with this MyBatis Pooling DataSource for Cursor queries
    // (parallelism)
    // as opposed to the spring managed DataSource
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
          () -> session.getMapper(IndexingMapper.class).getAllForDataset(2011),
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
      if (nameUsage.nomenclaturalCode != null)
        nomCode = NomCode.valueOf(nameUsage.nomenclaturalCode);
      ParsedName pn = NameParsers.INSTANCE.parse(nameUsage.scientificName, rank, nomCode);

      // canonicalMinimal will construct the name without the hybrid marker and authorship
      String canonical = org.gbif.nameparser.util.NameFormatter.canonicalMinimal(pn);
      optCanonical = Optional.ofNullable(canonical);
    } catch (UnparsableNameException | InterruptedException e) {
      // do nothing
      LOG.warn("Unable to parse name to create canonical: {}", nameUsage.scientificName);
    }

    final String canonical = optCanonical.orElse(nameUsage.scientificName);

    // use custom precision step as we do not need range queries and prefer to save memory usage
    // instead
    // FIXME check this is the correct "id" field
    doc.add(new StringField(FIELD_ID, nameUsage.id, Field.Store.YES));

    // we only store accepted key, no need to index it
    // FIXME re-check this understanding. If the name is a synonym, then parentId name usage points
    // to the accepted name
    if (nameUsage.status.equals(TaxonomicStatus.SYNONYM.name())) {
      doc.add(new StringField(FIELD_ACCEPTED_ID, nameUsage.parentId, Field.Store.YES));
    }

    // analyzed name field - this is what we search upon
    doc.add(new TextField(FIELD_CANONICAL_NAME, canonical, Field.Store.YES));

    // store full name and classification only to return a full match object for hits
    doc.add(new StringField(FIELD_SCIENTIFIC_NAME, nameUsage.scientificName, Field.Store.YES));

    // FIXME: old impl stored as int, but service returns string
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
