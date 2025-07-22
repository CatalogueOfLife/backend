package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.TaxonMetricsMapper;

import org.gbif.nameparser.api.Rank;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsDao implements TaxonCounter {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsDao.class);
  private final SqlSessionFactory factory;

  public MetricsDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  public int count(DSID<String> key, Rank countRank) {
    try (SqlSession session = factory.openSession(true)) {
      var tax = session.getMapper(TaxonMetricsMapper.class).get(key);
      return tax == null || !tax.getTaxaByRankCount().containsKey(countRank) ? 0 : tax.getTaxaByRankCount().get(countRank);
    }
  }

}
