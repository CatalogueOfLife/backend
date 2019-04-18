package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Name;
import org.col.api.model.NameRelation;
import org.col.api.vocab.NomRelType;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameDao extends DatasetEntityDao<Name, NameMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);
  
  public NameDao(SqlSessionFactory factory) {
    super(false, factory, NameMapper.class);
  }
  
  public Name getBasionym(int datasetKey, String id) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper rm = session.getMapper(NameRelationMapper.class);
      List<NameRelation> rels = rm.listByType(datasetKey, id, NomRelType.BASIONYM);
      if (rels.size() == 1) {
        NameMapper nm = session.getMapper(NameMapper.class);
        return nm.get(datasetKey, rels.get(0).getRelatedNameId());
      } else if (rels.size() > 1) {
        throw new IllegalStateException("Multiple basionyms found for name " + id);
      }
    }
    return null;
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
