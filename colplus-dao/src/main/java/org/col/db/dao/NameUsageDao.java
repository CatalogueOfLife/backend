package org.col.db.dao;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.SynonymMapper;
import org.col.db.mapper.TaxonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of methods dealing with name usages, i.e. a name in the context of either a Taxon, Synonym or BareName.
 */
public class NameUsageDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDao.class);

  private final SqlSession session;
  private final TaxonMapper tMapper;
  private final SynonymMapper sMapper;
  private final NameMapper nMapper;

  public NameUsageDao(SqlSession sqlSession) {
    this.session = sqlSession;
    tMapper = session.getMapper(TaxonMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
    nMapper = session.getMapper(NameMapper.class);
  }

  public List<NameUsage> usages(Name n) {
    List<NameUsage> usages = Lists.newArrayList();
    for (Taxon t : tMapper.getByName(n)) {
      t.setName(n);
      usages.add(t);
    }
    Synonym s = sMapper.getByName(n);
    if (s != null) {
      s.setName(n);
      usages.add(s);
    }
    if (usages.isEmpty()) {
      usages.add(new BareName(n));
    }
    return usages;
  }

  public ResultPage<NameUsage> search(NameSearch query, Page page) {
    if (query.isEmpty()) {
      // default to order by key for large, unfiltered resultssets
      query.setSortBy(NameSearch.SortBy.KEY);
    } else if (query.getSortBy() == null) {
      query.setSortBy(NameSearch.SortBy.NAME);
    }
    if (query.getQ() != null) {
      query.setQ(query.getQ() + ":*");
    }
    int total = 0;
    List<Name> hits = nMapper.search(query, page);
    List<NameUsage> usage = new ArrayList<>(hits.size());
    if (!hits.isEmpty()) {
      total = nMapper.countSearchResults(query);
      // now lookup each name ...
      for (Name n : hits) {
        usage.addAll(usages(n));
      }
    }
    return new ResultPage<>(page, total, usage);
  }

}
