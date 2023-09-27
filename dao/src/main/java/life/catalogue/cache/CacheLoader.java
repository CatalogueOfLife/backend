package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public interface CacheLoader {

  SimpleNameCached load(DSID<String> key);

  /**
   * Freshly inserted records are often not immediately visible, e.g. in batch inserts.
   * Provide a handler to flush/commit such records in case its needed.
   */
  void commit();

  class Mybatis implements CacheLoader {
    private final SqlSession session;
    private final NameUsageMapper num;

    public Mybatis(SqlSession session) {
      this.session = session;
      this.num = session.getMapper(NameUsageMapper.class);
    }

    @Override
    public SimpleNameCached load(DSID<String> key) {
      return num.getSimplePub(key);
    }

    @Override
    public void commit() {
      session.commit();
    }
  }

  class MybatisFactory implements CacheLoader {
    private final SqlSessionFactory factory;

    public MybatisFactory(SqlSessionFactory factory) {
      this.factory = factory;
    }

    @Override
    public SimpleNameCached load(DSID<String> key) {
      try (SqlSession session = factory.openSession()) {
        return session.getMapper(NameUsageMapper.class).getSimplePub(key);
      }
    }

    @Override
    public void commit() {
      // nothing to do
    }
  }
}
