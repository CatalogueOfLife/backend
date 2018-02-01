package org.col.dw.dao;

import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.col.dw.api.NameAct;
import org.col.dw.api.Page;
import org.col.dw.api.Reference;
import org.col.dw.api.ResultPage;
import org.col.dw.api.Taxon;
import org.col.dw.db.KeyNotFoundException;
import org.col.dw.db.NotInDatasetException;
import org.col.dw.db.mapper.NameActMapper;
import org.col.dw.db.mapper.ReferenceMapper;

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

  public Integer lookupKey(String id, int datasetKey) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    Integer key = mapper.lookupKey(id, datasetKey);
    if (key == null) {
      throw new NotInDatasetException(Taxon.class, datasetKey, id);
    }
    return key;
  }

  public Reference get(int key) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    Reference result = mapper.get(key);
    if (result == null) {
      throw new KeyNotFoundException(Reference.class, key);
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
