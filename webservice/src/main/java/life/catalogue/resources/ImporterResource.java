package life.catalogue.resources;

import com.google.common.base.Strings;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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
  public ResultPage<DatasetImport> list(@QueryParam("datasetKey") Integer datasetKey,
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
  public ImportRequest schedule(@Auth User user, @Valid ImportRequest request) {
    request.createdBy = user.getKey();
    return importManager.submit(request);
  }

  @POST
  @RolesAllowed({Roles.ADMIN})
  @Path("/restart")
  public boolean restart(@Auth User user) {
    LOG.warn("Restarting importer by {}", user);
    return importManager.restart();
  }

  @GET
  @Path("{key}")
  public DatasetImport get(@PathParam("key") int datasetKey){
    return dao.getLast(datasetKey);
  }

  @POST
  @Path("{key}")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest reimport(@PathParam("key") int datasetKey, @Auth User user) throws IOException {
    return importManager.submit(ImportRequest.reimport(datasetKey, user.getKey()));
  }

  @POST
  @Path("{key}")
  @Consumes({MoreMediaTypes.APP_GZIP, MoreMediaTypes.APP_ZIP, MediaType.APPLICATION_OCTET_STREAM})
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest uploadArchive(@PathParam("key") int datasetKey, @Auth User user, @Context HttpHeaders headers, InputStream archive) throws IOException {
    return importManager.upload(datasetKey, archive, false, null, user);
  }

  @POST
  @Path("{key}")
  @Consumes({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV,
      MoreMediaTypes.TEXT_YAML, MoreMediaTypes.APP_YAML,
      MoreMediaTypes.TEXT_WILDCARD})
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest uploadCsv(@PathParam("key") int datasetKey, @Auth User user, @Context HttpHeaders headers, InputStream archive) throws IOException {
    return importManager.upload(datasetKey, archive, true, contentType2Suffix(headers), user);
  }

  @POST
  @Path("{key}")
  @Consumes({MoreMediaTypes.APP_XLS, MoreMediaTypes.APP_XLSX})
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest uploadXls(@PathParam("key") int datasetKey, @Auth User user, InputStream spreadsheet) throws IOException {
    return importManager.uploadXls(datasetKey, spreadsheet ,user);
  }
  
  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void cancel(@PathParam("key") int datasetKey, @Auth User user) {
    importManager.cancel(datasetKey, user.getKey());
  }

  private static String contentType2Suffix(HttpHeaders h) {
    if (h != null && h.getRequestHeaders() != null) {
      String ctype = Strings.nullToEmpty(h.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).toLowerCase();
      switch (ctype) {
        case MoreMediaTypes.TEXT_CSV:
        case MoreMediaTypes.TEXT_COMMA_SEP:
          return "csv";
        case MoreMediaTypes.TEXT_TSV:
        case MoreMediaTypes.TEXT_TAB_SEP:
          return "tsv";
        case MoreMediaTypes.TEXT_YAML:
        case MoreMediaTypes.APP_YAML:
          return "yaml";
        case MediaType.TEXT_PLAIN:
        case MoreMediaTypes.TEXT_WILDCARD:
          return "txt";
        case MoreMediaTypes.APP_GZIP:
          return "gzip";
        case MoreMediaTypes.APP_ZIP:
        case MediaType.APPLICATION_OCTET_STREAM:
          return "zip";
      }
      // text wildcard
      if (ctype.startsWith("text/")){
        return "txt";
      }
    }
    return null;
  }
}
