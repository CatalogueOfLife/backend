package life.catalogue.dao;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.db.CRUD;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.EstimateMapper;
import life.catalogue.db.mapper.SectorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubjectRematcher {
  private static final Logger LOG = LoggerFactory.getLogger(SubjectRematcher.class);
  
  private final SqlSessionFactory factory;
  private DatasetMapper dm;
  private SectorMapper sm;
  private DecisionMapper dem;
  private EstimateMapper esm;
  private MatchingDao mdao;
  private final int catalogueKey;
  private final int userKey;
  
  private MatchCounter sectors   = new MatchCounter();
  private MatchCounter decisions = new MatchCounter();
  private MatchCounter estimates = new MatchCounter();
  private int datasets = 0;
  
  public SubjectRematcher(SqlSessionFactory factory, int catalogueKey, int userKey) {
    this.userKey = userKey;
    this.catalogueKey = catalogueKey;
    this.factory = factory;
  
  }
  
  private void init(SqlSession session) {
    dm = session.getMapper(DatasetMapper.class);
    sm = session.getMapper(SectorMapper.class);
    dem = session.getMapper(DecisionMapper.class);
    esm = session.getMapper(EstimateMapper.class);
    mdao = new MatchingDao(session);
  }
  
  static class MatchCounter {
    private int broken;
    private int updated;
    private int unchanged;
  
    public int getBroken() {
      return broken;
    }
  
    public int getUpdated() {
      return updated;
    }
  
    public int getUnchanged() {
      return unchanged;
    }
  
    public int getTotal() {
      return broken + updated + unchanged;
    }
  }
  
  public MatchCounter getSectors() {
    return sectors;
  }
  
  public MatchCounter getDecisions() {
    return decisions;
  }
  
  public MatchCounter getEstimates() {
    return estimates;
  }
  
  public int getDatasets() {
    return datasets;
  }
  
  private void matchAll(int catalogueKey) {
    LOG.info("Rematch all sectors, decisions and estimates across all datasets");
    Set<Integer> datasetKeys = new HashSet<>();
    Pager.sectors(catalogueKey, factory).forEach(s -> {
      datasetKeys.add(s.getSubjectDatasetKey());
      matchSector(s);
    });
  
    Pager.decisions(catalogueKey, factory).forEach(d -> {
      datasetKeys.add(d.getSubjectDatasetKey());
      matchDecision(d);
    });
    
    LOG.info("Rematch all estimates for draft catalogue");
    Pager.estimates(catalogueKey, factory).forEach(this::matchEstimate);

    datasets = datasetKeys.size();
  }
  
  public void match(RematchRequest req) {
    try(SqlSession session = factory.openSession(true)) {
      init(session);
      if (req.getEstimateKey() != null){
        matchEstimate(getNotNull(esm, req.getEstimateKey()));
        
      } else if (req.getDecisionKey() != null){
        matchDecision(getNotNull(dem, req.getDecisionKey()));
    
      } else if (req.getSectorKey() != null){
        matchSector(getNotNull(sm, req.getSectorKey()));
    
      } else if (req.getDatasetKey() != null){
        matchDataset(req.getDatasetKey());
  
      } else if (Boolean.TRUE.equals(req.getAll())) {
        matchAll(catalogueKey);
      }
      session.commit();
    }
    log();
  }
  
  public void matchDatasetSubjects(final int datasetKey) {
    LOG.info("Rematch all sector subjects in dataset {}", datasetKey);
    try(SqlSession session = factory.openSession(true)) {
      init(session);
      for (Sector s : sm.listByDataset(catalogueKey, datasetKey)) {
        matchSectorSubjectOnly(s);
      }
      matchDatasetDecision(datasetKey);
      session.commit();
    }
    log();
  }
  
  private static <T extends DataEntity<Integer>> T getNotNull(CRUD<Integer, T> mapper, int key) throws NotFoundException {
    T obj = mapper.get(key);
    if (obj == null) {
      throw new NotFoundException("Key " + key + " does not exist");
    }
    return obj;
  }

  private void matchSectorSubject(Sector s) {
    s.getSubject().setId(null);
    NameUsage t = matchUniquely(s, s.getSubjectDatasetKey(), s.getSubject());
    if (t != null) {
      // see if we already have a sector attached
      Sector s2 = sm.getBySubject(s.getDatasetKey(), s.getSubjectDatasetKey(), t.getId());
      if (s2 != null) {
        LOG.warn("Sector {} seems to be a duplicate of {} for {} in dataset {}", s, s2, t.getName().getScientificName(), s.getDatasetKey());
      } else {
        s.getSubject().setId(t.getId());
      }
    }
  }
  
  private void matchSectorTarget(Sector s) {
    NameUsage u = matchUniquely(s, s.getDatasetKey(), s.getTarget());
    s.getTarget().setId(u == null ? null : u.getId());
  }
  
  /**
   * @return true if the simple name id was changed
   */
  private boolean updateCounter(MatchCounter cnt, String idBefore, String idAfter) {
    boolean changed = !Objects.equals(idBefore, idAfter);
    if (idAfter == null) {
      cnt.broken++;
    } else if (changed) {
      cnt.updated++;
    } else {
      cnt.unchanged++;
    }
    return changed;
  }
  
  /**
   * @return true if the simple name id was changed
   */
  private boolean updateCounter(MatchCounter cnt, String sIdBefore, String sIdAfter, String tIdBefore, String tIdAfter) {
    boolean changed = !Objects.equals(sIdBefore, sIdAfter) || !Objects.equals(tIdBefore, tIdAfter);
    if (sIdAfter == null || tIdAfter == null) {
      cnt.broken++;
    } else if (changed) {
      cnt.updated++;
    } else {
      cnt.unchanged++;
    }
    return changed;
  }

  private void matchDecision(EditorialDecision ed) {
    if (ed.getSubject() != null) {
      NameUsage u = matchUniquely(ed, ed.getSubjectDatasetKey(), ed.getSubject());
      String idBefore = ed.getSubject().getId();
      ed.getSubject().setId(u == null ? null : u.getId());
      if (updateCounter(decisions, idBefore, ed.getSubject().getId())) {
        dem.update(ed);
      }
    }
  }
  
  private void matchEstimate(SpeciesEstimate est) {
    if (est.getTarget() != null) {
      NameUsage u = matchUniquely(est, est.getDatasetKey(), est.getTarget());
      String idBefore = est.getTarget().getId();
      est.getTarget().setId(u == null ? null : u.getId());
      if (updateCounter(decisions, idBefore, est.getTarget().getId())) {
        esm.update(est);
      }
    }
  }
  
  private void matchSector(Sector s) {
    if (s.getSubject() != null && s.getTarget() != null) {
      String sIdBefore = s.getSubject().getId();
      String tIdBefore = s.getTarget().getId();
      
      matchSectorSubject(s);
      matchSectorTarget(s);
      
      if (updateCounter(sectors, sIdBefore, s.getSubject().getId(), tIdBefore, s.getTarget().getId())) {
        sm.update(s);
      }
    }
  }
  
  private void matchSectorSubjectOnly(Sector s) {
    if (s.getSubject() != null) {
      String sIdBefore = s.getSubject().getId();
      matchSectorSubject(s);
      if (updateCounter(sectors, sIdBefore, s.getSubject().getId())) {
        sm.update(s);
      }
    }
  }
  
  private void matchDataset(final int datasetKey) {
    LOG.info("Rematch all sector subjects in dataset {}", datasetKey);
    for (Sector s : sm.listByDataset(catalogueKey, datasetKey)) {
      matchSector(s);
    }
    matchDatasetDecision(datasetKey);
  }
  
  private void matchDatasetDecision(final int datasetKey) {
    LOG.info("Rematch all decision subjects in dataset {}", datasetKey);
    datasets++;
    for (EditorialDecision d : dem.listBySubjectDataset(catalogueKey, datasetKey, null)) {
      matchDecision(d);
    }
  }
  
  private void log() {
    LOG.info("Found {} distinct datasets during rematching", datasets);
    log("sectors", sectors);
    log("decisions", decisions);
    log("estimates", estimates);
  }
  
  private void log(String type, MatchCounter cnt) {
    if (cnt.getTotal() > 0) {
      LOG.info("Rematched {} {} . updated: {}, broken: {}", cnt.getTotal(), type, cnt.updated, cnt.broken);
    }
  }
  
  private NameUsage matchUniquely(DataEntity<Integer> d, int datasetKey, SimpleName sn){
    List<? extends NameUsage> matches = mdao.matchDataset(sn, datasetKey);
    if (matches.isEmpty()) {
      LOG.warn("{} {} cannot be rematched to dataset {} - lost {}", d.getClass().getSimpleName(), d.getKey(), datasetKey, sn);
    } else if (matches.size() > 1) {
      LOG.warn("{} {} cannot be rematched to dataset {} - multiple names like {}", d.getClass().getSimpleName(), d.getKey(), datasetKey, sn);
    } else {
      return matches.get(0);
    }
    return null;
  }
  
}
