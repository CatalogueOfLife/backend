package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.TypeMaterialMapper;
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

import com.google.common.base.Preconditions;

public class NameDao extends SectorEntityDao<Name, NameMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);
  private final NameUsageIndexService indexService;
  private final NameIndex nameIndex;

  public NameDao(SqlSessionFactory factory, NameUsageIndexService indexService, NameIndex nameIndex, Validator validator) {
    super(false, factory, Name.class, NameMapper.class, validator);
    this.indexService = indexService;
    this.nameIndex = nameIndex;
  }

  ResultPage<Name> search(int datasetKey, NameMapper.NameSearchRequest filter, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(mapperClass);
      var result = mapper.search(datasetKey, filter, p);
      return new ResultPage<>(p, result, () -> mapper.count(datasetKey));
    }
  }
  public void createRelation(Name from, NomRelType type, Name to, int user) {
    Preconditions.checkArgument(from.getDatasetKey().equals(to.getDatasetKey()));
    createRelation(from.getDatasetKey(), from.getId(), type, to.getId(), user);
  }

  public void createRelation(int datasetKey, String nameID, NomRelType type, String relatedNameID, int user) {
    try (SqlSession session = factory.openSession(true)) {
      NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);

      NameRelation rel = new NameRelation();
      rel.setDatasetKey(datasetKey);
      rel.applyUser(user);
      rel.setNameId(nameID);
      rel.setRelatedNameId(relatedNameID);
      rel.setType(type);
      nrm.create(rel);
    }
  }

  @Override
  protected boolean createAfter(Name n, int user, NameMapper mapper, SqlSession session) {
    // create name match
    NameMatch m = nameIndex.match(n, true, false);
    session.getMapper(NameMatchMapper.class).create(n, n.getSectorKey(), m.getNameKey(),m.getType());
    n.applyMatch(m);
    return true;
  }

  @Override
  protected boolean updateAfter(Name n, Name old, int user, NameMapper mapper, SqlSession session, boolean keepSessionOpen) {
    // update name match
    NameMatch m = nameIndex.match(n, true, false);
    session.getMapper(NameMatchMapper.class).update(n,m.getNameKey(), m.getType());
    n.applyMatch(m);
    return true;
  }

  public Name getBasionym(DSID<String> key) {
    return getBasionym(factory, key);
  }

  public static Name getBasionym(SqlSessionFactory factory, DSID<String> key) {
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

  public int deleteOrphans(int datasetKey, @Nullable LocalDateTime before, int userKey) {
    final int cnt;
    LOG.info("Remove orphaned names data from dataset {}", datasetKey);
    try (SqlSession session = factory.openSession(false)) {
      PgUtils.deferConstraints(session);
      cnt = session.getMapper(NameMapper.class).deleteOrphans(datasetKey, before);
      LOG.info("Removed {} orphan names from dataset {} by user {}", cnt, datasetKey, userKey);
      // also remove orphaned name relations and type material in the same session to not break the FK constraints
      int cnt2 = session.getMapper(NameRelationMapper.class).deleteOrphans(datasetKey, before);
      LOG.info("Removed {} orphan name relations from dataset {} by user {}", cnt2, datasetKey, userKey);
      cnt2 = session.getMapper(TypeMaterialMapper.class).deleteOrphans(datasetKey, before);
      LOG.info("Removed {} orphan type materials from dataset {} by user {}", cnt2, datasetKey, userKey);
      cnt2 = session.getMapper(NameMatchMapper.class).deleteOrphans(datasetKey);
      LOG.info("Removed {} orphan name matches from dataset {} by user {}", cnt2, datasetKey, userKey);
      session.commit();
    }
    // also remove from ES
    int cnt2 = indexService.deleteBareNames(datasetKey);
    LOG.info("Removed {} bare names from ES index for dataset {}", cnt2, datasetKey);
    return cnt;
  }

  /**
   * Lists all homotypic names based on name relations for a given name.
   * The given "start" name is included from the result.
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
