package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.db.dao.DatasetDao;
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
  public ResultPage<Dataset> list(@Valid @BeanParam Page page, @QueryParam("q") String q,
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
                                        @QueryParam("all") boolean all,
                                        @Context SqlSession session) {
    DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
    if (all) {
      return mapper.listByDataset(key);
    } else {
      return Lists.newArrayList(mapper.lastSuccessful(key));
    }
  }

}
