package org.col.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.col.api.model.ColUser;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.vocab.ImportState;
import org.col.dao.DatasetImportDao;
import org.col.dw.auth.Roles;
import org.col.dw.jersey.MoreMediaTypes;
import org.col.importer.ImportManager;
import org.col.importer.ImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/importer")
@Produces(MediaType.APPLICATION_JSON)
public class ImporterResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ImporterResource.class);
  private final ImportManager importManager;
  private final DatasetImportDao dao;
  
  public ImporterResource(ImportManager importManager, DatasetImportDao diDao) {
    this.importManager = importManager;
    dao = diDao;
  }
  
  @GET
  public List<DatasetImport> list(@QueryParam("datasetKey") Integer datasetKey,
                                        @QueryParam("state") List<ImportState> states,
                                        @QueryParam("running") Boolean running,
                                        @Valid @BeanParam Page page) {
    if (running != null) {
      states = running ? ImportState.runningStates() : ImportState.finishedStates();
    }
    return importManager.listImports(datasetKey, states, page);
  }
  
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest schedule(@Auth ColUser user, @Valid ImportRequest request) {
    request.createdBy = user.getKey();
    return importManager.submit(request);
  }
  
  @GET
  @Path("{key}")
  public DatasetImport get(@PathParam("key") int datasetKey){
    return dao.getLast(datasetKey);
  }
  
  @POST
  @Path("{key}")
  @Consumes({MoreMediaTypes.APP_GZIP, MoreMediaTypes.APP_ZIP, MediaType.APPLICATION_OCTET_STREAM})
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest upload(@PathParam("key") int datasetKey, @Auth ColUser user, InputStream archive) throws IOException {
    return importManager.submit(datasetKey, archive, user);
  }
  
  
  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void cancel(@PathParam("key") int datasetKey, @Auth ColUser user) {
    importManager.cancel(datasetKey, user.getKey());
  }
  
}
