package org.col.resources;

import java.util.List;
import javax.validation.constraints.NotNull;
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
public class SectorResource extends CRUDResource<Sector> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorResource.class);

  public SectorResource() {
    super(Sector.class, SectorMapper.class);
  }
  
  @GET
  public List<Sector> list(@Context SqlSession session, @NotNull @QueryParam("sourceKey") Integer colSourceKey) {
    return session.getMapper(SectorMapper.class).list(colSourceKey);
  }

}
