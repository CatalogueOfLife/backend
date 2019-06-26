package org.col.dao;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.DatasetID;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
import org.col.api.search.ReferenceSearchRequest;
import org.col.common.csl.CslUtil;
import org.col.db.mapper.ReferenceMapper;

public class ReferenceDao extends DatasetEntityDao<Reference, ReferenceMapper> {
  
  
  public ReferenceDao(SqlSessionFactory factory) {
    super(false, factory, ReferenceMapper.class);
  }
  
  public Reference get(int datasetKey, String id, @Nullable String page) {
    Reference ref = super.get(datasetKey, id);
    if (ref == null) {
      return null;
    }
    if (page != null) {
      ref.setPage(page);
    }
    return ref;
  }
  
  @Override
  public String create(Reference r, int user) {
    if (r.getCitation() == null && r.getCsl() != null) {
      // build citation from csl
      r.setCitation(CslUtil.buildCitation(r.getCsl()));
    }
    return super.create(r, user);
  }
  
  @Override
  protected void updateBefore(Reference r, Reference old, int user, ReferenceMapper mapper, SqlSession session) {
    if (r.getCitation() == null && r.getCsl() != null) {
      // build citation from csl
      r.setCitation(CslUtil.buildCitation(r.getCsl()));
    } else if (Objects.equals(r.getCitation(), old.getCitation()) && !Objects.equals(r.getCsl(), old.getCsl())) {
      // csl changed, but citation is still the same
      r.setCitation(CslUtil.buildCitation(r.getCsl()));
    }
  }
  
  /**
   * Copies the given nam instance, modifying the original and assigning a new id
   */
  public static DatasetID copyReference(final SqlSession session, final Reference r, final int targetDatasetKey, int user) {
    final DatasetID orig = new DatasetID(r);
    newKey(r);
    r.applyUser(user, true);
    r.setDatasetKey(targetDatasetKey);
    session.getMapper(ReferenceMapper.class).create(r);
    return orig;
  }
  
  public ResultPage<Reference> search(int datasetKey, ReferenceSearchRequest req, Page page) {
    page = page == null ? new Page() : page;
    req = req == null || req.isEmpty() ? new ReferenceSearchRequest() : req;
    if (req.getSortBy() == null) {
      if (!StringUtils.isBlank(req.getQ())) {
        req.setSortBy(ReferenceSearchRequest.SortBy.RELEVANCE);
      } else {
        req.setSortBy(ReferenceSearchRequest.SortBy.NATIVE);
      }
    } else if (req.getSortBy() == ReferenceSearchRequest.SortBy.RELEVANCE && StringUtils.isBlank(req.getQ())) {
      req.setQ(null);
      req.setSortBy(ReferenceSearchRequest.SortBy.NATIVE);
    }
    
    try (SqlSession session = factory.openSession()) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      List<Reference> result = mapper.search(datasetKey, req, page);
      int total = result.size() == page.getLimit() ? mapper.searchCount(datasetKey, req) : page.getOffset() + result.size();
      return new ResultPage<>(page, total, result);
    }
  }
}
