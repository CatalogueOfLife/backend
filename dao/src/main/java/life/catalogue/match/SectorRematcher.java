package life.catalogue.match;

import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.Sector;
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
    return rematcher.match(dao.getFactory());
  }

  private SectorRematcher(SectorDao dao, SectorRematchRequest req, int userKey) {
    super(Sector.class, SectorMapper.class, req, userKey);
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
    if (req.isSubject() && obj.getSubject() != null) {
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
    if (req.isTarget() && obj.getTarget() != null) {
      NameUsage u = matchTargetUniquely(obj, obj.getTarget());
      obj.getTarget().setId(null);
      if (u != null) {
        obj.getTarget().setId(u.getId());
      }
    }
    // counter
    if (updateCounter(old.getSubject().getId(), obj.getSubject().getId(),   old.getTarget().getId(), obj.getTarget().getId())) {
      dao.update(obj, old, userKey, session);
    }
  }
  
}
