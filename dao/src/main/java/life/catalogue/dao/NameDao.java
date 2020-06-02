package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.List;

public class NameDao extends DatasetStringEntityDao<Name, NameMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);
  private final NameUsageIndexService indexService;

  public NameDao(SqlSessionFactory factory, NameUsageIndexService indexService) {
    super(false, factory, NameMapper.class);
    this.indexService = indexService;
  }
  
  public Name getBasionym(DSID<String> did) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper rm = session.getMapper(NameRelationMapper.class);
      List<NameRelation> rels = rm.listByType(did.getDatasetKey(), did.getId(), NomRelType.BASIONYM);
      if (rels.size() == 1) {
        NameMapper nm = session.getMapper(NameMapper.class);
        return nm.get(new DSIDValue<>(did.getDatasetKey(), rels.get(0).getRelatedNameId()));
      } else if (rels.size() > 1) {
        throw new IllegalStateException("Multiple basionyms found for name " + did.getId());
      }
    }
    return null;
  }

  public ResultPage<Name> listOrphans(int datasetKey, @Nullable LocalDateTime before, @Nullable Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      NameMapper nm = session.getMapper(NameMapper.class);
      List<Name> result = nm.listOrphans(datasetKey, before, p);
      return new ResultPage<>(p, result, () -> page.getLimit()+1);
    }
  }

  public int deleteOrphans(int datasetKey, @Nullable LocalDateTime before, User user) {
    try (SqlSession session = factory.openSession()) {
      int cnt = session.getMapper(NameMapper.class).deleteOrphans(datasetKey, before);
      session.commit();
      LOG.info("Removed {} orphan names from dataset {} by user {}", cnt, datasetKey, user);
      // also remove from ES
      int cnt2 = indexService.deleteBareNames(datasetKey);
      LOG.info("Removed {} bare names from ES index for dataset {}", cnt2, datasetKey);
      return cnt;
    }
  }

  /**
   * Lists all homotypic synonyms based on the same homotypic group key
   */
  public List<Name> homotypicGroup(int datasetKey, String id) {
    try (SqlSession session = factory.openSession(false)) {
      NameMapper nm = session.getMapper(NameMapper.class);
      return nm.homotypicGroup(datasetKey, id);
    }
  }
  
  /**
   * Lists all relations for a given name and type
   */
  public List<NameRelation> relations(int datasetKey, String id, NomRelType type) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper rm = session.getMapper(NameRelationMapper.class);
      return rm.listByType(datasetKey, id, type);
    }
  }
  
  /**
   * Lists all relations for a given name
   */
  public List<NameRelation> relations(int datasetKey, String id) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper rm = session.getMapper(NameRelationMapper.class);
      return rm.list(datasetKey, id);
    }
  }
  
}
