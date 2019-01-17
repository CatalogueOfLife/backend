package org.col.resources;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Sector;
import org.col.db.mapper.SectorMapper;
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
  
  @GET
  public List<Sector> list(@Context SqlSession session,
                           @QueryParam("datasetKey") Integer datasetKey,
                           @QueryParam("sourceKey") Integer colSourceKey) {
    return session.getMapper(SectorMapper.class).list(datasetKey, colSourceKey);
  }
  
  @GET
  @Path("/broken")
  public List<Sector> broken(@Context SqlSession session,
                             @QueryParam("target") boolean target,
                             @QueryParam("datasetKey") Integer datasetKey,
                             @QueryParam("sourceKey") Integer colSourceKey) {
    SectorMapper mapper = session.getMapper(SectorMapper.class);
    if (target) {
      return mapper.targetBroken(datasetKey, colSourceKey);
    } else {
      return mapper.subjectBroken(datasetKey, colSourceKey);
    }
  }
}
