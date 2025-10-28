package life.catalogue.resources.dataset;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.db.mapper.VerbatimSourceMapper;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

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
