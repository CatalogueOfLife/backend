package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.metadata.DoiResolver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;
import jakarta.validation.Validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReferenceDao extends SectorEntityDao<Reference, ReferenceMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceDao.class);
  private final DoiResolver resolver;

  public ReferenceDao(SqlSessionFactory factory, DoiResolver resolver, Validator validator) {
    super(false, factory, Reference.class, ReferenceMapper.class, validator);
    this.resolver = resolver;
  }
  
  public Reference get(DSID<String> did, @Nullable String page) {
    Reference ref = super.get(did);
    if (ref == null) {
      return null;
    }
    if (page != null) {
      ref.setPage(page);
    }
    return ref;
  }

  public DSID<String> create(int datasetKey, CslData csl, int user) {
    if (csl.getId() == null) {
      csl.setId(UUID.randomUUID().toString());
    }
    Reference ref = new ReferenceFactory(datasetKey, resolver).fromCsl(datasetKey, csl, IssueContainer.VOID);
    return create(ref, user);
  }

  @Override
  public DSID<String> create(Reference r, int user) {
    // build default citation from csl
    if (r == null) {
      throw new IllegalArgumentException("No reference given");
    }
    if (r.getCitation() == null && r.getCsl() != null) {
      r.setCitation(CslUtil.buildCitation(r.getCsl()));
    }
    if (r.getCitation() == null) {
      throw new IllegalArgumentException("Empty reference given");
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
  public static DSIDValue<String> copyReference(final SqlSession session, final Reference r, final int targetDatasetKey, int user) {
    final DSIDValue<String> orig = new DSIDValue<>(r);
    newKey(r);
    r.applyUser(user, true);
    r.setDatasetKey(targetDatasetKey);
    session.getMapper(ReferenceMapper.class).create(r);
    return orig;
  }
  
  public ResultPage<Reference> search(int datasetKey, @Nullable ReferenceSearchRequest request, Page page) {
    page = page == null ? new Page() : page;
    final ReferenceSearchRequest req = request == null || request.isEmpty() ? new ReferenceSearchRequest() : request;
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
      return new ResultPage<>(page, result, () -> mapper.searchCount(datasetKey, req));
    }
  }

  public ResultPage<Reference> listOrphans(int datasetKey, LocalDateTime before, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      ReferenceMapper nm = session.getMapper(ReferenceMapper.class);
      List<Reference> result = nm.listOrphans(datasetKey, before, p);
      return new ResultPage<>(p, result, () -> page.getLimit()+1);
    }
  }

  public int deleteOrphans(int datasetKey, @Nullable LocalDateTime before, int userKey) {
    LOG.info("Remove orphaned references from dataset {}", datasetKey);
    try (SqlSession session = factory.openSession()) {
      int cnt = session.getMapper(ReferenceMapper.class).deleteOrphans(datasetKey, before);
      session.commit();
      LOG.info("Remove {} orphan references from dataset {} by user {}", cnt, datasetKey, userKey);
      return cnt;
    }
  }
}
