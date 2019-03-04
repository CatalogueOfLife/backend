package org.col.admin.assembly;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Sector;
import org.col.api.model.Taxon;
import org.col.db.dao.MatchingDao;
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
      
      int counter = 0;
      int failed  = 0;
      for (Sector s : sm.list(datasetKey)) {
        List<Taxon> matches = mdao.matchDataset(s.getSubject(), s.getDatasetKey());
        if (matches.isEmpty()) {
          LOG.warn("Sector {} cannot be rematched to dataset {} - lost {}", s.getKey(), s.getDatasetKey(), s.getSubject());
          s.getSubject().setId(null);
          failed++;
        } else if (matches.size() > 1) {
          LOG.warn("Sector {} cannot be rematched to dataset {} - multiple names like {}", s.getKey(), s.getDatasetKey(), s.getSubject());
          s.getSubject().setId(null);
          failed++;
        } else {
          s.getSubject().setId(matches.get(0).getId());
          counter++;
        }
        sm.update(s);
      }
      LOG.info("Rematched {} sectors, {} failed", counter, failed);

      counter = 0;
      failed = 0;
      for (EditorialDecision e : em.list(datasetKey, null)) {
        List<Taxon> matches = mdao.matchDataset(e.getSubject(), e.getDatasetKey());
        if (matches.isEmpty()) {
          LOG.warn("Decision {} cannot be rematched to dataset {} - lost {}", e.getKey(), e.getDatasetKey(), e.getSubject());
          e.getSubject().setId(null);
          failed++;
        } else if (matches.size() > 1) {
          LOG.warn("Decision {} cannot be rematched to dataset {} - multiple names like {}", e.getKey(), e.getDatasetKey(), e.getSubject());
          e.getSubject().setId(null);
          failed++;
        } else {
          e.getSubject().setId(matches.get(0).getId());
          counter++;
        }
        em.update(e);
        counter++;
      }
      LOG.info("Rematched {} decisions, {} failed", counter, failed);
    }
  }
  
}
