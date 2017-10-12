package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Path("/dataset")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetResource {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);

  @GET
  public PagingResultSet<Dataset> list(@Valid @BeanParam Page page, @Context SqlSession session) {
    DatasetMapper mapper = session.getMapper(DatasetMapper.class);
    return new PagingResultSet<Dataset>(page, mapper.count(), mapper.list(page));
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
