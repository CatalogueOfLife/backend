package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.dao.DatasetDao;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DatasetResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);

  @GET
  public PagingResultSet<Dataset> list(@Valid @BeanParam Page page, @QueryParam("q") String q,
      @Context SqlSession session) {
    return new DatasetDao(session).search(q, page);
  }

  @POST
  public Integer create(Dataset dataset, @Context SqlSession session) {
    session.getMapper(DatasetMapper.class).create(dataset);
    session.commit();
    return dataset.getKey();
  }

  @GET
  @Path("{key}")
  public Dataset get(@PathParam("key") Integer key, @Context SqlSession session) {
    return session.getMapper(DatasetMapper.class).get(key);
  }

  @PUT
  @Path("{key}")
  public Response update(Dataset dataset, @Context SqlSession session) {
    int i = session.getMapper(DatasetMapper.class).update(dataset);
    session.commit();
    if (i == 0) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @DELETE
  @Path("{key}")
  public Response delete(@PathParam("key") Integer key, @Context SqlSession session) {
    int i = session.getMapper(DatasetMapper.class).delete(key);
    session.commit();
    if (i == 0) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @GET
  @Path("{key}/import")
  public List<DatasetImport> getImports(@PathParam("key") Integer key,
      @Context SqlSession session) {
    return session.getMapper(DatasetImportMapper.class).list(key);
  }

  @GET
  @Path("{key}/import/last")
  public DatasetImport getLastImport(@PathParam("key") Integer key, @Context SqlSession session) {
    return session.getMapper(DatasetImportMapper.class).lastSuccessful(key);
  }

}
