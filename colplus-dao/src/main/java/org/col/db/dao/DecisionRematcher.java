package org.col.db.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.search.DatasetSearchRequest;
import org.col.api.vocab.Datasets;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.DecisionMapper;
import org.col.db.mapper.SectorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionRematcher implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(DecisionRematcher.class);
  private final SqlSessionFactory factory;
  private final Integer datasetKey;
  private SectorMapper sm;
  private DecisionMapper em;
  private MatchingDao mdao;
  private int sectorTotal = 0;
  private int sectorFailed  = 0;
  private int decisionTotal = 0;
  private int decisionFailed  = 0;
  private int datasets  = 0;
  
  public DecisionRematcher(SqlSessionFactory factory) {
    this(factory, null);
  }

  public DecisionRematcher(SqlSessionFactory factory, Integer datasetKey) {
    this.factory = factory;
    this.datasetKey = datasetKey;
  }
  
  @Override
  public void run() {
    try (SqlSession session = factory.openSession(true)) {
      sm = session.getMapper(SectorMapper.class);
      em = session.getMapper(DecisionMapper.class);
      mdao = new MatchingDao(session);
      
      if (datasetKey == null) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        DatasetSearchRequest req = new DatasetSearchRequest();
        req.setContributesTo(Datasets.DRAFT_COL);
        req.setSortBy(DatasetSearchRequest.SortBy.SIZE);
        Page page = new Page(0, 20);
        List<Dataset> resp = null;
        while(resp == null || resp.get(resp.size()-1).getSize() != null) {
          resp = dm.search(req, page);
          for (Dataset d : resp) {
            matchDataset(d.getKey());
          }
        }
      } else {
        matchDataset(datasetKey);
      }
      if (datasetKey == null) {
        // log totals across all datasets
        LOG.info("Rematched {} sectors from all {} datasets, {} failed", sectorTotal, datasets, sectorFailed);
        LOG.info("Rematched {} decisions from all {} datasets, {} failed", decisionTotal, datasets, decisionFailed);
      }
    }
  }
  
  private String matchUniquely(Decision d, int datasetKey, SimpleName sn){
    List<Taxon> matches = mdao.matchDataset(sn, datasetKey);
    if (matches.isEmpty()) {
      LOG.warn("{} {} cannot be rematched to dataset {} - lost {}", d.getClass().getSimpleName(), d.getKey(), datasetKey, sn);
    } else if (matches.size() > 1) {
      LOG.warn("{} {} cannot be rematched to dataset {} - multiple names like {}", d.getClass().getSimpleName(), d.getKey(), datasetKey, sn);
    } else {
      return matches.get(0).getId();
    }
    return null;
  }
  
  public boolean matchSector(Sector s) {
    String id = matchUniquely(s, s.getDatasetKey(), s.getSubject());
    boolean success = id != null;
    s.getSubject().setId(id);
    if (s.getTarget().getId() == null) {
      String id2 = matchUniquely(s, Datasets.DRAFT_COL, s.getTarget());
      s.getTarget().setId(id2);
      success = success && id2 != null;
    }
    sm.update(s);
    return success;
  }
  
  public boolean matchDecision(EditorialDecision ed) {
    String id = matchUniquely(ed, ed.getDatasetKey(), ed.getSubject());
    boolean success = id != null;
    ed.getSubject().setId(id);
    em.update(ed);
    return success;
  }

  private void matchDataset(final int datasetKey) {
    datasets++;
    int counter = 0;
    int failed  = 0;
    for (Sector s : sm.list(datasetKey)) {
      if (!matchSector(s)){
        failed++;
      }
      counter++;
    }
    sectorTotal  += counter;
    sectorFailed += failed;
    LOG.info("Rematched {} sectors from dataset {}, {} failed", counter, datasetKey, failed);
    
    counter = 0;
    failed = 0;
    for (EditorialDecision e : em.list(datasetKey, null)) {
      if (!matchDecision(e)){
        failed++;
      }
      counter++;
    }
    decisionTotal  += counter;
    decisionFailed += failed;
    LOG.info("Rematched {} decisions from dataset {}, {} failed", counter, datasetKey, failed);
  }
}
