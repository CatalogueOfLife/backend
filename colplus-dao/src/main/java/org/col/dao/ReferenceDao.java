package org.col.dao;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
import org.col.db.mapper.ReferenceMapper;

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
  
  public Reference get(int datasetKey, String id, @Nullable String page) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    Reference ref = mapper.get(datasetKey, id);
    if (ref == null) {
      return null;
    }
    if (page != null) {
      ref.setPage(page);
    }
    return ref;
  }
  
  public void create(Reference ref) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    mapper.create(ref);
  }
  
}
