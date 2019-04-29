package org.col.dao;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.MatchingMode;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.DuplicateMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateDao {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateDao.class);
  
  private final SqlSession session;
  private final DuplicateMapper mapper;
  
  public DuplicateDao(SqlSession sqlSession) {
    this.session = sqlSession;
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  public List<Duplicate> find(MatchingMode mode, Integer minSize, int datasetKey, Integer sectorKey, Rank rank, Set<TaxonomicStatus> status, Boolean authorshipDifferent, Boolean parentDifferent, Boolean withDecision, Page page) {
    mode = ObjectUtils.defaultIfNull(mode, MatchingMode.STRICT);
    minSize = ObjectUtils.defaultIfNull(minSize, 2);
    Preconditions.checkArgument(minSize > 1, "minimum group size must at least be 2");
    
    // load all duplicate usages
    List<Duplicate.Mybatis> dupsTmp = mapper.duplicates(mode, minSize, datasetKey, sectorKey, rank, status, authorshipDifferent, parentDifferent, withDecision, page);
    if (dupsTmp.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    
    List<String> ids = dupsTmp.stream()
        .map(Duplicate.Mybatis::getUsages)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  
    Map<String, Duplicate.UsageDecision> usages = mapper.usagesByIds(datasetKey, ids).stream()
        .collect(Collectors.toMap(d -> d.getUsage().getId(), Function.identity()));
    
    List<Duplicate> dups = new ArrayList<>(dupsTmp.size());
    for (Duplicate.Mybatis dm : dupsTmp) {
      Duplicate d = new Duplicate();
      d.setKey(dm.getKey());
      d.setUsages(dm.getUsages().stream()
          .map(usages::get)
          .collect(Collectors.toList())
      );
      dups.add(d);
    }
    return dups;
  }
}
