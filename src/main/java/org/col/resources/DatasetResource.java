package org.col.resources;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;


@Path("/dataset")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetResource {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);

  @GET
  public PagingResultSet<Dataset> list(Page page, @Context SqlSession session) {
    DatasetMapper mapper = session.getMapper(DatasetMapper.class);
    return new PagingResultSet<Dataset>(mapper.count(), mapper.list(page));
  }

  @POST
  public Integer create(Dataset dataset, @Context SqlSession session) {
    DatasetMapper mapper = session.getMapper(DatasetMapper.class);
    mapper.create(dataset);
    session.commit();
    return dataset.getKey();
  }

  @GET
  @Path("{key}")
  public Dataset get(@PathParam("key") Integer key, @Context SqlSession session) {
    DatasetMapper mapper = session.getMapper(DatasetMapper.class);
    return mapper.get(key);
  }

  @PUT
  @Path("{key}")
  public void update(Dataset dataset, @Context SqlSession session) {
    DatasetMapper mapper = session.getMapper(DatasetMapper.class);
    mapper.update(dataset);
    session.commit();
  }

  @DELETE
  @Path("{key}")
  public void delete(@PathParam("key") Integer key, @Context SqlSession session) {
    DatasetMapper mapper = session.getMapper(DatasetMapper.class);
    mapper.delete(key);
    session.commit();
  }

}
