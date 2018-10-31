package org.col.admin.resources;

import java.util.List;
import java.util.Queue;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.importer.ImportManager;
import org.col.admin.importer.ImportRequest;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.ImportState;
import org.col.db.dao.DatasetImportDao;
import org.col.dw.auth.Roles;
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
  public ResultPage<DatasetImport> list(@QueryParam("state") List<ImportState> states, @Valid @BeanParam Page page) {
    return dao.list(states, page);
  }

  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest schedule(@QueryParam("key") Integer datasetKey, @QueryParam("force") boolean force) {
    return importManager.submit(datasetKey, force);
  }
  
  @POST
  @Path("/upload/{datasetKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest upload(@PathParam("datasetKey") int datasetKey) {
    throw new NotImplementedException("Manual upload not yet implemented");
  }
  
  @GET
  @Path("/queue")
  public Queue<ImportRequest> queue() {
    return importManager.list();
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void cancel(@PathParam("key") Integer datasetKey) {
    importManager.cancel(datasetKey);
  }

}
