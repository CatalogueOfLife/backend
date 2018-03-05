package org.col.db.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.NotFoundException;
import org.col.db.mapper.NameActMapper;
import org.col.db.mapper.ReferenceMapper;

import java.util.List;

public class ReferenceDao {

  private final SqlSession session;

  public ReferenceDao(SqlSession sqlSession) {
    this.session = sqlSession;
  }

  public int count(int datasetKey) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    return mapper.count(datasetKey);
  }

  public ResultPage<Reference> list(int datasetKey, Page page) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    int total = mapper.count(datasetKey);
    List<Reference> result = mapper.list(datasetKey, page);
    return new ResultPage<>(page, total, result);
  }

  public Integer lookupKey(String id, int datasetKey) throws NotFoundException {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    Integer key = mapper.lookupKey(id, datasetKey);
    if (key == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return key;
  }

  public Reference get(int key) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    Reference result = mapper.get(key);
    if (result == null) {
      throw NotFoundException.keyNotFound(Reference.class, key);
    }
    return result;
  }

  public List<NameAct> getNameActs(int referenceKey) {
    NameActMapper naMapper = session.getMapper(NameActMapper.class);
    return naMapper.listByReference(referenceKey);
  }

  public void create(Reference ref) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    mapper.create(ref);
  }

}
