package life.catalogue.matching.decision;

import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.SectorMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorRematcher extends RematcherBase<Sector, SectorRematchRequest, SectorSearchRequest, SectorMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(SectorRematcher.class);

  private final SectorDao dao;

  public static MatchCounter match(SectorDao dao, SectorRematchRequest req, int userKey){
    SectorRematcher rematcher = new SectorRematcher(dao, req, userKey);
    return rematcher.match();
  }

  private SectorRematcher(SectorDao dao, SectorRematchRequest req, int userKey) {
    super(Sector.class, SectorMapper.class, req, userKey, dao.getFactory());
    this.dao = dao;
  }

  @Override
  SectorSearchRequest toSearchRequest(SectorRematchRequest req) {
    return req.buildSearchRequest();
  }

  @Override
  void match(Sector obj) {
    final Sector old = new Sector(obj);
    // subject
    if (needsRematching(req.isSubject(), obj.getSubject())) {
      LOG.debug("Match subject {} of sector {} in project {}", obj.getSubject(), obj.getId(), projectKey);
      // we dont want to let the parent break a subject - thats mostly useful for editorial decision
      obj.getSubject().setParent(null);
      NameUsage u = matchSubjectUniquely(obj.getSubjectDatasetKey(), obj, obj.getSubject(), obj.getOriginalSubjectId());
      obj.getSubject().setId(null);
      if (u != null) {
        // see if we already have another sector with the same subject ID
        Sector s2 = mapper.getBySubject(projectKey, u);
        if (s2 != null && !s2.getId().equals(obj.getId())) {
          LOG.warn("Sector {} seems to be a duplicate of {} for subject {} in project {}. Keep sector {} broken", obj, s2, u.getName().getScientificName(), projectKey, obj.getId());
        } else {
          obj.getSubject().setId(u.getId());
        }
      }
    }
    // target can have multiple sectors
    if (needsRematching(req.isTarget(), obj.getTarget())) {
      LOG.debug("Match target {} of sector {} in project {}", obj.getTarget(), obj.getId(), projectKey);
      // we dont want to let the parent break a target - thats mostly useful for editorial decision
      obj.getTarget().setParent(null);
      NameUsage u = matchTargetUniquely(obj, obj.getTarget());
      obj.getTarget().setId(null);
      if (u != null) {
        obj.getTarget().setId(u.getId());
      }
    }
    // counter
    if (updateCounter(obj.getSubject() != null, old.getSubjectID(), obj.getSubjectID(),
                      obj.getTarget()  != null, old.getTargetID(), obj.getTargetID())
    ) {
      dao.update(obj, old, userKey, session);
    }
  }

  private static boolean needsRematching(Boolean flag, SimpleNameLink sn){
    if (sn == null) return false;
    if (flag != null) return flag;
    return sn.isBroken() || sn.getId() == null;
  }
  
}
