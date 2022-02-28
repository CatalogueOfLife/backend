package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.es.NameUsageIndexService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.Validator;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class DecisionDao extends DatasetEntityDao<Integer, EditorialDecision, DecisionMapper> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionDao.class);

  private final NameUsageIndexService indexService;

  public DecisionDao(SqlSessionFactory factory, NameUsageIndexService indexService, Validator validator) {
    super(true, factory, EditorialDecision.class, DecisionMapper.class, validator);
    this.indexService = indexService;
  }

  public ResultPage<EditorialDecision> search(DecisionSearchRequest request, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      DecisionMapper mapper = session.getMapper(DecisionMapper.class);
      List<EditorialDecision> result = mapper.search(request, p);
      return new ResultPage<>(p, result, () -> mapper.countSearch(request));
    }
  }

  /**
   * Lists all projects that have at least one decision on the given subject dataset key.
   */
  public List<Integer> listProjects(Integer subjectDatasetKey) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(DecisionMapper.class).listProjectKeys(subjectDatasetKey);
    }
  }

  @Override
  protected boolean createAfter(EditorialDecision obj, int user, DecisionMapper mapper, SqlSession session) {
    session.close(); // close early as indexing can take some time and we might see 100 concurrent requests
    if (obj.getSubject().getId() != null) {
      indexService.update(obj.getSubjectDatasetKey(), Lists.newArrayList(obj.getSubject().getId()));
    }
    return false;
  }

  /**
   * Updates the decision in Postgres and updates the ES index for the taxon linked to the subject id. If the previous
   * version referred to a different subject id also update that taxon.
   */
  @Override
  protected boolean updateAfter(EditorialDecision obj, EditorialDecision old, int user, DecisionMapper mapper, SqlSession session, boolean keepSessionOpen) {
    if (!keepSessionOpen) {
      session.close(); // close early, indexing can take some time
    }
    final List<String> ids = new ArrayList<>();
    if (old != null && old.getSubject().getId() != null && !old.getSubject().getId().equals(obj.getSubject().getId())) {
      ids.add(old.getSubject().getId());
    }
    if (obj.getSubject().getId() != null) {
      ids.add(obj.getSubject().getId());
    }
    indexService.update(obj.getSubjectDatasetKey(), ids);
    return keepSessionOpen;
  }

  @Override
  protected boolean deleteAfter(DSID<Integer> key, EditorialDecision old, int user, DecisionMapper mapper, SqlSession session) {
    session.close(); // close early, indexing can take some time
    if (old != null && old.getSubject().getId() != null) {
      indexService.update(old.getSubjectDatasetKey(), Lists.newArrayList(old.getSubject().getId()));
    }
    return false;
  }

}
