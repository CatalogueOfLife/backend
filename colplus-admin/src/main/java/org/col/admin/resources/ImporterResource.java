package org.col.admin.resources;

import java.util.Queue;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.importer.ImportManager;
import org.col.admin.importer.ImportRequest;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.ImportState;
import org.col.db.dao.DatasetImportDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/importer")
@Produces(MediaType.APPLICATION_JSON)
public class ImporterResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ImporterResource.class);
  private final ImportManager importManager;
  private final DatasetImportDao dao;

  public ImporterResource(ImportManager importManager, SqlSessionFactory factory) {
    this.importManager = importManager;
    dao = new DatasetImportDao(factory);
  }

  @GET
  public ResultPage<DatasetImport> list(@QueryParam("state") ImportState state, @Valid @BeanParam Page page) {
    return dao.list(state, page);
  }

  @POST
  public ImportRequest schedule(@QueryParam("key") Integer datasetKey, @QueryParam("force") boolean force) {
    return importManager.submit(datasetKey, force);
  }

  @GET
  @Path("/queue")
  public Queue<ImportRequest> queue() {
    return importManager.list();
  }

  @DELETE
  @Path("{key}")
  public void cancel(@PathParam("key") Integer datasetKey) {
    importManager.cancel(datasetKey);
  }

}
