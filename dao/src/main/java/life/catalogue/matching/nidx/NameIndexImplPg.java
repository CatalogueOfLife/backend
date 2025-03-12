package life.catalogue.matching.nidx;

import life.catalogue.api.model.IndexName;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NamesIndexMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Extends the regular names index with a postgres backing,
 * storing all index names in pg as the main reference and making sure on startup that the index and pg match up
 */
public class NameIndexImplPg extends NameIndexImpl {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexImplPg.class);

  private final SqlSessionFactory sqlFactory;
  private final boolean verifyIndex; // if true compares counts from index with postgres counts and reloads if wrong

  /**
   * @param store
   * @param normalizer
   * @param sqlFactory sql session factory to talk to the data store backend if needed for inserts or initial loading
   * @throws IllegalStateException when db is in a bad state
   */
  public NameIndexImplPg(NameIndexStore store, AuthorshipNormalizer normalizer, SqlSessionFactory sqlFactory, boolean verifyIndex) {
    super(store, normalizer);
    this.sqlFactory = Preconditions.checkNotNull(sqlFactory);
    this.verifyIndex = verifyIndex;
  }

  private int countPg() {
    try (SqlSession s = sqlFactory.openSession()) {
      return s.getMapper(NamesIndexMapper.class).count();
    }
  }

  public void printIndex() {
    System.out.println("\nNames Index from postgres:");
    try (SqlSession session = sqlFactory.openSession(true)) {
      session.getMapper(NamesIndexMapper.class).processAll().forEach(System.out::println);
    }
  }

  private void loadFromPg() {
    store.clear();
    LOG.info("Loading names from postgres into names index");
    try (SqlSession s = sqlFactory.openSession()) {
      NamesIndexMapper mapper = s.getMapper(NamesIndexMapper.class);
      PgUtils.consume(
        () -> mapper.processAll(),
        this::addFromPg
      );
      LOG.info("Loaded {} names from postgres into names index", store.count());
    }
  }

  private void addFromPg(IndexName name) {
    final String key = key(name);
    store.add(key, name);
  }

  protected void prepareQualified(String key, IndexName n){
    try (SqlSession s = sqlFactory.openSession(true)) {
      NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);
      nim.create(n);
    }
  }

  protected void prepareCanonical(String key, IndexName cn){
    try (SqlSession s = sqlFactory.openSession(true)) {
      NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);
      // mybatis defaults canonicalID to the newly created key in the database...
      nim.create(cn);
    }
    // ... but the instance is not updated automatically
    cn.setCanonicalId(cn.getKey());
  }

  protected void createCanonical(NamesIndexMapper nim, String key, IndexName cn){
  }

  @Override
  public void start() throws Exception {
    LOG.info("Start names index ...");
    store.start();
    int storeSize = store.count();
    if (storeSize == 0) {
      loadFromPg();
    } else {
      // verify postgres and store match up - otherwise trust postgres
      int pgCount = countPg();
      if (pgCount != storeSize) {
        LOG.warn("Existing name index contains {} names, but postgres has {}.", storeSize, pgCount);
        if (verifyIndex) {
          loadFromPg();
        }
      }
    }
    LOG.info("Started name index with {} names", store.count());
  }
}
