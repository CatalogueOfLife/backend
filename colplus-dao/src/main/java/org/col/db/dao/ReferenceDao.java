package org.col.db.dao;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
import org.col.api.model.Taxon;
import org.col.db.NotFoundException;
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

  public ResultPage<Reference> list(Integer datasetKey, Page page) {
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

  public Reference get(int key, @Nullable String page) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    Reference ref = mapper.get(key);
    if (ref == null) {
      throw NotFoundException.keyNotFound(Reference.class, key);
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
