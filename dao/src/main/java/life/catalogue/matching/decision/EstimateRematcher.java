package life.catalogue.matching.decision;

import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.dao.EstimateDao;
import life.catalogue.db.mapper.EstimateMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EstimateRematcher extends RematcherBase<SpeciesEstimate, RematchRequest, EstimateSearchRequest, EstimateMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(EstimateRematcher.class);

  private final EstimateDao dao;

  public static MatchCounter match(EstimateDao dao, RematchRequest req, int userKey){
    EstimateRematcher rematcher = new EstimateRematcher(dao, req, userKey);
    return rematcher.match();
  }

  private EstimateRematcher(EstimateDao dao, RematchRequest req, int userKey) {
    super(SpeciesEstimate.class, EstimateMapper.class, req, userKey, dao.getFactory());
    this.dao = dao;
  }

  @Override
  EstimateSearchRequest toSearchRequest(RematchRequest req) {
    EstimateSearchRequest sr = new EstimateSearchRequest();
    sr.setDatasetKey(req.getDatasetKey());
    sr.setBroken(req.isBroken());
    return sr;
  }

  @Override
  void match(SpeciesEstimate obj) {
    if (obj.getTarget() != null) {
      final SpeciesEstimate old = new SpeciesEstimate(obj);
      // we dont want to let the parent break a subject - thats mostly useful for editorial decision
      obj.getTarget().setParent(null);
      NameUsage u = matchTargetUniquely(obj, obj.getTarget());
      obj.getTarget().setId(u == null ? null : u.getId());
      if (updateCounter(old.getTarget().getId(), obj.getTarget().getId())) {
        dao.update(obj, old, userKey, session);
      }
    }
  }
  
}
