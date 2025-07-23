package life.catalogue.resources.dataset;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.LogicalOperator;
import life.catalogue.db.mapper.VerbatimRecordMapper;

import life.catalogue.db.mapper.VerbatimSourceMapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;

import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

@Path("/dataset/{key}/verbatimsource")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class VerbatimSourceResource {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimSourceResource.class);
  private final SqlSessionFactory factory;

  public VerbatimSourceResource(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @GET
  public List<VerbatimSource> list(@PathParam("key") int datasetKey,
                                   @QueryParam("sourceDatasetKey") Integer sourceDatasetKey,
                                   @QueryParam("sectorKey") Integer sectorKey,
                                   @QueryParam("sourceEntity") EntityType sourceEntity,
                                   @QueryParam("secondarySourceKey") Integer secondarySourceKey,
                                   @QueryParam("secondarySourceGroup") InfoGroup secondarySourceGroup
  ) {
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(VerbatimSourceMapper.class);
      return mapper.list(datasetKey, sourceDatasetKey, sectorKey, sourceEntity, secondarySourceKey, secondarySourceGroup);
    }
  }

  @GET
  @Path("{id}")
  public VerbatimSource get(@PathParam("key") int datasetKey, @PathParam("id") int id) {
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(VerbatimSourceMapper.class);
      return mapper.get(DSID.of(datasetKey, id));
    }
  }
  
}
