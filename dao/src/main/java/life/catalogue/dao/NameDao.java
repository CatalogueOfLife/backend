package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndex;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.Validator;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameDao extends DatasetStringEntityDao<Name, NameMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);
  private final NameUsageIndexService indexService;
  private final NameIndex nameIndex;

  public NameDao(SqlSessionFactory factory, NameUsageIndexService indexService, NameIndex nameIndex, Validator validator) {
    super(false, factory, Name.class, NameMapper.class, validator);
    this.indexService = indexService;
    this.nameIndex = nameIndex;
  }

  @Override
  protected boolean createAfter(Name n, int user, NameMapper mapper, SqlSession session) {
    // create name match
    NameMatch m = nameIndex.match(n, true, false);
    if (m.hasMatch()) {
      session.getMapper(NameMatchMapper.class).create(n, n.getSectorKey(), m.getNameKey(), m.getType());
    }
    return true;
  }

  @Override
  protected boolean updateAfter(Name n, Name old, int user, NameMapper mapper, SqlSession session, boolean keepSessionOpen) {
    // update name match
    NameMatch m = nameIndex.match(n, true, false);
    if (m.hasMatch()) {
      session.getMapper(NameMatchMapper.class).update(n, m.getNameKey(), m.getType());
    } else {
      session.getMapper(NameMatchMapper.class).delete(n);
    }
    return true;
  }

  public Name getBasionym(DSID<String> key) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper rm = session.getMapper(NameRelationMapper.class);
      List<NameRelation> rels = rm.listByType(key, NomRelType.BASIONYM);
      if (rels.size() == 1) {
        NameMapper nm = session.getMapper(NameMapper.class);
        return nm.get(new DSIDValue<>(key.getDatasetKey(), rels.get(0).getRelatedNameId()));
      } else if (rels.size() > 1) {
        throw new IllegalStateException("Multiple basionyms found for name " + key.getId());
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
   * Lists all homotypic names based on name relations for a given name
   */
  public List<Name> homotypicGroup(DSID<String> key) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
      var ids = nrm.listRelatedNameIDs(key, NomRelType.HOMOTYPIC_RELATIONS);
      ids.remove(key.getId());
      if (!ids.isEmpty()) {
        NameMapper nm = session.getMapper(NameMapper.class);
        return nm.listByIds(key.getDatasetKey(), Set.copyOf(ids));
      }
      return Collections.emptyList();
    }
  }
  
  /**
   * Lists all relations for a given name and type
   */
  public List<NameRelation> relations(DSID<String> key, NomRelType type) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper rm = session.getMapper(NameRelationMapper.class);
      return rm.listByType(key, type);
    }
  }
  
  /**
   * Lists all relations for a given name
   */
  public List<NameRelation> relations(DSID<String> key) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper rm = session.getMapper(NameRelationMapper.class);
      return rm.listByName(key);
    }
  }
  
}
