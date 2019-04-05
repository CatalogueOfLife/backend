package org.col.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.dao.DecisionRematcher;
import org.col.dao.SectorDao;
import org.col.dao.TaxonDao;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/sector")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorResource extends CRUDIntResource<Sector> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorResource.class);
  private final SqlSessionFactory factory;
  
  public SectorResource(SqlSessionFactory factory) {
    super(Sector.class, new SectorDao(factory));
    this.factory = factory;
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Override
  public Integer create(@Valid Sector obj, @Auth ColUser user) {
    try(SqlSession session = factory.openSession(false)) {
      final DatasetID did = obj.getTargetAsDatasetID();
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      
      // reload full source and target
      Taxon subject = tm.get(obj.getDatasetKey(), obj.getSubject().getId());
      if (subject == null) {
        throw new IllegalArgumentException("subject ID " + obj.getSubject().getId() + " not existing in dataset " + obj.getDatasetKey());
      }
      obj.setSubject(subject.toSimpleName());
  
      Taxon target  = tm.get(Datasets.DRAFT_COL, obj.getTarget().getId());
      if (target == null) {
        throw new IllegalArgumentException("target ID " + obj.getTarget().getId() + " not existing in draft CoL");
      }
      obj.setTarget(target.toSimpleName());
  
      // create sector
      Integer secKey = super.create(obj, user);
    
      TaxonDao tdao = new TaxonDao(session);
      List<Taxon> toCopy = new ArrayList<>();
      // create direct children in catalogue
      if (Sector.Mode.ATTACH == obj.getMode()) {
        // one taxon in ATTACH mode
        toCopy.add(subject);
      } else {
        // several taxa in MERGE mode
        toCopy = tm.children(obj.getDatasetKey(), obj.getSubject().getId(), new Page());
      }
    
      for (Taxon t : toCopy) {
        t.setSectorKey(obj.getKey());
        tdao.copyTaxon(t, did, user, Collections.emptySet());
      }
      session.commit();
    
      return secKey;
    }
  }
  
  @Override
  public void delete(Integer key, @Auth ColUser user) {
    // do not allow to delete a sector directly
    // instead an asyncroneous sector deletion should be triggered in the admin-ws which also removes catalogue data
    throw new NotAllowedException("Sectors cannot be deleted directly. Use the assembly service instead");
  }
  
  @GET
  public List<Sector> list(@Context SqlSession session,
                           @QueryParam("datasetKey") Integer datasetKey) {
    return session.getMapper(SectorMapper.class).listByDataset(datasetKey);
  }
  
  @GET
  @Path("/broken")
  public List<Sector> broken(@Context SqlSession session,
                             @QueryParam("target") boolean target,
                             @NotNull @QueryParam("datasetKey") Integer datasetKey) {
    SectorMapper mapper = session.getMapper(SectorMapper.class);
    if (target) {
      return mapper.targetBroken(datasetKey);
    } else {
      return mapper.subjectBroken(datasetKey);
    }
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Path("/{key}/rematch")
  public Sector rematch(@PathParam("key") Integer key, @Context SqlSession session, @Auth ColUser user) {
    Sector s = getNonNull(key);
    new DecisionRematcher(session).matchSector(s, true, true);
    session.commit();
    return s;
  }
  
}
