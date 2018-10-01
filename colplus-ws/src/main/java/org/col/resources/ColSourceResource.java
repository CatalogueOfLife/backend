package org.col.resources;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.ColSource;
import org.col.db.mapper.ColSourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/colsource")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ColSourceResource extends CRUDResource<ColSource> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ColSourceResource.class);

  public ColSourceResource() {
    super(ColSource.class, ColSourceMapper.class);
  }
  
  @GET
  public List<ColSource> list(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey) {
    return session.getMapper(ColSourceMapper.class).list(datasetKey);
  }

  @GET
  @Path("{key}/edit")
  public ColSource getEditable(@Context SqlSession session, @Param("key") int key) {
    return session.getMapper(ColSourceMapper.class).getEditable(key);
  }

}
