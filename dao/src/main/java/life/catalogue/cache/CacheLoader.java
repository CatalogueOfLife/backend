package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;

public interface CacheLoader {

  SimpleNameCached load(String key);

  /**
   * Freshly inserted records are often not immediately visible, e.g. in batch inserts.
   * Provide a handler to flush/commit such records in case its needed.
   */
  void commit();

  class MybatisSession implements CacheLoader {
    private final SqlSession session;
    private final int datasetKey;
    private final DSID<String> dsid;
    private final NameUsageMapper num;

    /**
     * @param session to use for loading records
     */
    public MybatisSession(SqlSession session, int datasetKey) {
      this.datasetKey = datasetKey;
      this.dsid = DSID.root(datasetKey);
      this.session = session;
      this.num = session.getMapper(NameUsageMapper.class);
    }

    @Override
    public SimpleNameCached load(String key) {
      return num.getSimpleCached(dsid.id(key));
    }

    @Override
    public void commit() {
      session.commit();
    }
  }
}
