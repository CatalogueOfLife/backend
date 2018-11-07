package org.col.admin.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Queue;
import javax.annotation.security.RolesAllowed;
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
import org.col.dw.auth.Roles;
import org.col.dw.jersey.MoreMediaTypes;
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
  public ResultPage<DatasetImport> list(@QueryParam("datasetKey") Integer datasetKey,
                                        @QueryParam("state") List<ImportState> states,
                                        @Valid @BeanParam Page page) {
    return dao.list(datasetKey, states, page);
  }

  @GET
  @Path("/queue")
  public Queue<ImportRequest> queue() {
    return importManager.list();
  }
  
  @POST
  @Path("{key}")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest schedule(@PathParam("key") int datasetKey, @QueryParam("force") boolean force) {
    return importManager.submit(datasetKey, force);
  }
  
  @POST
  @Path("{key}")
  @Consumes({MoreMediaTypes.APP_GZIP, MoreMediaTypes.APP_ZIP, MediaType.APPLICATION_OCTET_STREAM})
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest upload(@PathParam("key") int datasetKey, InputStream archive) throws IOException {
    return importManager.submit(datasetKey, archive);
  }
  

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void cancel(@PathParam("key") int datasetKey) {
    importManager.cancel(datasetKey);
  }

}
