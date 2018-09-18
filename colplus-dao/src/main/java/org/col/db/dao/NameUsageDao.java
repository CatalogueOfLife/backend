package org.col.db.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.db.mapper.NameUsageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of methods dealing with name usages, i.e. a name in the context of either a Taxon, Synonym or BareName.
 * Mostly exposed by searches.
 */
public class NameUsageDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDao.class);

  private final SqlSession session;
  private final NameUsageMapper mapper;

  public NameUsageDao(SqlSession sqlSession) {
    this.session = sqlSession;
    mapper = session.getMapper(NameUsageMapper.class);
  }

  public List<NameUsage> byName(Name name) {
    return byNameId(name.getDatasetKey(), name.getId());
  }

  public List<NameUsage> byNameId(int datasetKey, String nameId) {
    return mapper.listByName(datasetKey, nameId);
  }

}
