package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.search.FacetValue;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.indexing.NameUsageIndexService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import jakarta.validation.Validator;

public class DecisionDao extends DatasetEntityDao<Integer, EditorialDecision, DecisionMapper> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionDao.class);

  private final NameUsageIndexService indexService;

  public DecisionDao(SqlSessionFactory factory, NameUsageIndexService indexService, Validator validator) {
    super(true, factory, EditorialDecision.class, DecisionMapper.class, validator);
    this.indexService = indexService;
  }

  public FacettedResultPage<EditorialDecision, String> search(DecisionSearchRequest request, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      DecisionMapper mapper = session.getMapper(DecisionMapper.class);
      List<EditorialDecision> result = mapper.search(request, p);
      Map<String, List<FacetValue<?>>> facets = null;
      if (request.getFacets().contains("mode")) {
        facets = Map.of("mode", mapper.searchModeFacet(request));
      }
      return new FacettedResultPage<>(p, result, () -> mapper.countSearch(request), facets);
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
  public DSID<Integer> create(EditorialDecision obj, int user) {
    try (SqlSession session = factory.openSession()) {
      obj.setSubject(refreshSubject(obj.getSubjectDatasetKey(), obj.getSubject(), null, session.getMapper(NameUsageMapper.class)));
    }
    return super.create(obj, user);
  }

  @Override
  protected void updateBefore(EditorialDecision obj, EditorialDecision old, int user, DecisionMapper mapper, SqlSession session) {
    obj.setSubject(refreshSubject(obj.getSubjectDatasetKey(), obj.getSubject(), old.getSubject(), session.getMapper(NameUsageMapper.class)));
  }

  /**
   * The subject is a denormalised copy of the linked usage, so a rematch after the source changed its ids can tell
   * homonyms apart: https://github.com/CatalogueOfLife/backend/issues/1639
   * A newly linked id copies name, authorship, rank, code, status and the parent name from its usage, the parent of a
   * synonym being its accepted name. The parent is kept as a name, as the ids it would otherwise refer to are just
   * what changes in a re-import.
   * The stored link is kept while its id is unchanged, so a stale link stays visible until it gets rematched.
   * An id that does not resolve keeps what was given, so the broken link can still be rematched by name.
   */
  static SimpleNameLink refreshSubject(Integer datasetKey, SimpleNameLink link, SimpleNameLink old, NameUsageMapper num) {
    if (link == null || link.getId() == null || datasetKey == null) {
      return link;
    }
    if (old != null && link.getId().equals(old.getId())) {
      return old;
    }
    SimpleName sn = num.getSimple(DSID.of(datasetKey, link.getId()));
    if (sn == null) {
      return link;
    }
    var subject = SimpleNameLink.of(sn);
    subject.setParent(null);
    if (sn.getParent() != null) {
      SimpleName parent = num.getSimple(DSID.of(datasetKey, sn.getParent()));
      if (parent != null) {
        subject.setParent(parent.getName());
      }
    }
    return subject;
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

  /**
   * We need a way to report ambiguous synonym update decisions that have only one name left in the project.
   * Ambiguous synonyms should have at least 2 names (accepted or synonym) which share the same canonical name & rank, but can differ in authorship.
   * This method lists those decisions only, so they can manually be fixed or removed.
   * @param projectKey
   */
  public ResultPage<EditorialDecision> listStaleAmbiguousUpdateDecisions(int projectKey, Integer subjectDatasetKey) {
    final int limit = 100;
    try (SqlSession session = factory.openSession()) {
      var ids = session.getMapper(DecisionMapper.class).listStaleAmbiguousUpdateDecisions(projectKey, subjectDatasetKey, Integer.MAX_VALUE);
      var eds = ids.subList(0, Math.min(limit, ids.size())).stream()
        .map(id -> DSID.of(projectKey, id))
        .map(this::get)
        .collect(Collectors.toList());
      return new ResultPage(new Page(0, limit), eds, () -> ids.size());
    }
  }
}
