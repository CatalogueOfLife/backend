package life.catalogue.matching.decision;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.mapper.DecisionMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionRematcher extends RematcherBase<EditorialDecision, DecisionRematchRequest, DecisionSearchRequest, DecisionMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(DecisionRematcher.class);

  private final DecisionDao dao;

  public static MatchCounter match(DecisionDao dao, DecisionRematchRequest req, int userKey){
    DecisionRematcher rematcher = new DecisionRematcher(dao, req, userKey);
    return rematcher.match();
  }

  private DecisionRematcher(DecisionDao dao, DecisionRematchRequest req, int userKey) {
    super(EditorialDecision.class, DecisionMapper.class, req, userKey, dao.getFactory());
    this.dao = dao;
  }

  @Override
  DecisionSearchRequest toSearchRequest(DecisionRematchRequest req) {
    return req.buildSearchRequest();
  }

  @Override
  void match(EditorialDecision obj) {
    if (obj.getSubject() != null) {
      final EditorialDecision old = new EditorialDecision(obj);
      obj.getSubject().setId(null);
      NameUsage u = matchSubjectUniquely(obj.getSubjectDatasetKey(), obj, obj.getSubject(), obj.getOriginalSubjectId());
      if (u != null) {
        // see if we already have another decision with the same subject ID
        EditorialDecision ed2 = mapper.getBySubject(projectKey, obj.getSubjectDatasetKey(), u.getId());
        if (ed2 != null && !ed2.getId().equals(obj.getId())) {
          LOG.warn("Decision {} seems to be a duplicate of {} for {} in project {}. Keep decision {} broken", obj, ed2, obj.getSubject(), projectKey, obj.getKey());
        } else {
          obj.getSubject().setId(u.getId());
        }
      }
      if (updateCounter(true, old.getSubject().getId(), obj.getSubject().getId())) {
        dao.update(obj, old, userKey, session);
      }
    }
  }
  
}
