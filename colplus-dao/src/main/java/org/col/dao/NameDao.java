package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.NameRelation;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.NomRelType;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.col.db.mapper.SynonymMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameDao {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);
  
  private final SqlSession session;
  private final NameMapper nMapper;
  private final NameRelationMapper nrMapper;
  private final SynonymMapper sMapper;
  
  public NameDao(SqlSession sqlSession) {
    this.session = sqlSession;
    nMapper = session.getMapper(NameMapper.class);
    nrMapper = session.getMapper(NameRelationMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
  }
  
  public int count(int datasetKey) {
    return nMapper.count(datasetKey);
  }
  
  public ResultPage<Name> list(int datasetKey, Page page) {
    int total = nMapper.count(datasetKey);
    List<Name> result = nMapper.list(datasetKey, page);
    return new ResultPage<>(page, total, result);
  }
  
  public Name get(int datasetKey, String id) {
    return nMapper.get(datasetKey, id);
  }
  
  public Name getBasionym(int datasetKey, String id) {
    List<NameRelation> rels = nrMapper.listByType(datasetKey, id, NomRelType.BASIONYM);
    if (rels.size() == 1) {
      return nMapper.get(datasetKey, rels.get(0).getRelatedNameId());
    } else if (rels.size() > 1) {
      throw new IllegalStateException("Multiple basionyms found for name " + id);
    }
    return null;
  }
  
  public void create(Name name) {
    nMapper.create(name);
  }
  
  /**
   * Lists all homotypic synonyms based on the same homotypic group key
   */
  public List<Name> homotypicGroup(int datasetKey, String id) {
    return nMapper.homotypicGroup(datasetKey, id);
  }
  
  /**
   * Lists all relations for a given name and type
   */
  public List<NameRelation> relations(int datasetKey, String id, NomRelType type) {
    return nrMapper.listByType(datasetKey, id, type);
  }
  
  /**
   * Lists all relations for a given name
   */
  public List<NameRelation> relations(int datasetKey, String id) {
    return nrMapper.list(datasetKey, id);
  }
  
}
