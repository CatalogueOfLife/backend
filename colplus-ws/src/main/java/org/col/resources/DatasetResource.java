package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.DatasetSearchRequest;
import org.col.api.vocab.ImportState;
import org.col.db.dao.DatasetDao;
import org.col.db.dao.DatasetImportDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DatasetResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);
  private final SqlSessionFactory factory;

  public DatasetResource(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @GET
  public ResultPage<Dataset> list(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req,
                                  @Context SqlSession session) {
    return new DatasetDao(session).search(req, page);
  }

  @POST
  public Integer create(Dataset dataset, @Context SqlSession session) {
    return new DatasetDao(session).create(dataset);
  }

  @GET
  @Path("{key}")
  public Dataset get(@PathParam("key") int key, @Context SqlSession session) {
    return new DatasetDao(session).get(key);
  }

  @PUT
  @Path("{key}")
  public void update(Dataset dataset, @Context SqlSession session) {
    new DatasetDao(session).update(dataset);
  }

  @DELETE
  @Path("{key}")
  public void delete(@PathParam("key") Integer key, @Context SqlSession session) {
    new DatasetDao(session).delete(key);
  }

  @GET
  @Path("{key}/import")
  public List<DatasetImport> getImports(@PathParam("key") int key,
                                        @QueryParam("limit") @DefaultValue("1") int limit,
                                        @QueryParam("state") ImportState state) {
    return new DatasetImportDao(factory).listByDataset(key, state, limit);
  }

}
