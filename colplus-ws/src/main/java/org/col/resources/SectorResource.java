package org.col.resources;

import java.util.Collections;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.SectorMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/sector")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorResource extends CRUDIntResource<Sector> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorResource.class);
  
  public SectorResource() {
    super(Sector.class, SectorMapper.class);
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer create(@Valid Sector obj, @Auth ColUser user, @Context SqlSession session) {
    Integer secKey = super.create(obj, user, session);
    // create direct children in catalogue
    if (Sector.Mode.ATTACH == obj.getMode()) {
      // one taxon in ATTACH mode
      TaxonDao tdao = new TaxonDao(session);
      tdao.copyTaxon(obj.getSourceAsDatasetID(), obj.getTargetAsDatasetID(), user, Collections.emptySet());
      session.commit();
    } else {
      // several taxa in MERGE mode
      TaxonDao tdao = new TaxonDao(session);
      final DatasetID did = obj.getTargetAsDatasetID();
      for (Taxon t : tdao.getChildren(obj.getDatasetKey(), obj.getSubject().getId(), new Page()).getResult()) {
        tdao.copyTaxon(t, did, user, Collections.emptySet());
      }
      session.commit();
    }
    return secKey;
  }
  
  @GET
  public List<Sector> list(@Context SqlSession session,
                           @QueryParam("datasetKey") Integer datasetKey) {
    return session.getMapper(SectorMapper.class).list(datasetKey);
  }
  
  @GET
  @Path("/broken")
  public List<Sector> broken(@Context SqlSession session,
                             @QueryParam("target") boolean target,
                             @QueryParam("datasetKey") Integer datasetKey) {
    SectorMapper mapper = session.getMapper(SectorMapper.class);
    if (target) {
      return mapper.targetBroken(datasetKey);
    } else {
      return mapper.subjectBroken(datasetKey);
    }
  }
}
