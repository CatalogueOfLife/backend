package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.dropwizard.auth.Auth;

@Path("/importer")
@Produces(MediaType.APPLICATION_JSON)
public class ImporterResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ImporterResource.class);
  private final ImportManager importManager;
  private final DatasetImportDao dao;
  private final DatasetDao ddao;
  private final WsServerConfig cfg;

  public ImporterResource(WsServerConfig cfg, ImportManager importManager, DatasetImportDao diDao, DatasetDao ddao) {
    this.importManager = importManager;
    dao = diDao;
    this.ddao = ddao;
    this.cfg = cfg;
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
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed({Roles.ADMIN})
  @Path("/batch")
  public int scheduleMultipleExternal(@Auth User user, @Valid DatasetSearchRequest request) {
    // enforce to only schedule external datasets, never projects or releases
    request.setOrigin(List.of(DatasetOrigin.EXTERNAL));
    final List<Integer> keys = ddao.searchKeys(request);
    LOG.warn("Scheduling {} dataset imports", keys.size());
    int counter = 0;
    for (int key : keys) {
      try {
        ImportRequest req = ImportRequest.external(key, user.getKey(), true);
        req.createdBy = user.getKey();

        importManager.submit(req);
        counter++;

      } catch (IllegalArgumentException e) {
        LOG.warn("Queue appears to be full ({}). Stop scheduling after {} imports", importManager.queueSize(), counter, e);
        break;
      }
    }
    LOG.info("Scheduled {} datasets for importing", counter);
    return counter;
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
  @Path("{key}/reimport")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest reimport(@PathParam("key") int datasetKey, @Auth User user) throws IOException {
    File latest = cfg.normalizer.lastestArchive(datasetKey);
    if (latest == null) {
      throw new IllegalArgumentException("No previous archive existing for dataset "+datasetKey+" to reimport");
    }
    int attempt = NormalizerConfig.attemptFromArchive(latest);
    return importManager.submit(ImportRequest.reimport(datasetKey, user.getKey(), attempt));
  }

  @POST
  @Path("{key}")
  // there are many unofficial mime types around for zip and gzip
  // these can show up via the upload component of the CLB UI if used from Windows for example, so we add them all
  @Consumes({
    MediaType.APPLICATION_OCTET_STREAM,
    MoreMediaTypes.APP_GZIP, MoreMediaTypes.APP_GZIP_ALT1, MoreMediaTypes.APP_GZIP_ALT2, MoreMediaTypes.APP_GZIP_ALT3,
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest uploadArchive(@PathParam("key") int datasetKey, @Auth User user, @Context HttpHeaders headers, InputStream archive) throws IOException {
    return importManager.upload(datasetKey, archive, false, filenameFromHeaders(headers), null, user);
  }

  @POST
  @Path("{key}")
  @Consumes({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV,
      MoreMediaTypes.TEXT_YAML, MoreMediaTypes.APP_YAML,
      MoreMediaTypes.TEXT_WILDCARD})
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest uploadCsv(@PathParam("key") int datasetKey, @Auth User user, @Context HttpHeaders headers, InputStream archive) throws IOException {
    return importManager.upload(datasetKey, archive, true, filenameFromHeaders(headers), contentType2Suffix(headers), user);
  }

  @POST
  @Path("{key}")
  @Consumes({MoreMediaTypes.APP_XLS, MoreMediaTypes.APP_XLSX})
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public ImportRequest uploadXls(@PathParam("key") int datasetKey, @Auth User user, @Context HttpHeaders headers, InputStream spreadsheet) throws IOException {
    return importManager.uploadXls(datasetKey, spreadsheet ,user);
  }
  
  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void cancel(@PathParam("key") int datasetKey, @Auth User user) {
    importManager.cancel(datasetKey, user.getKey());
  }

  private static String filenameFromHeaders(HttpHeaders h) {
    if (h != null && h.getRequestHeaders() != null) {
      return h.getRequestHeaders().getFirst(MoreHttpHeaders.FILENAME);
    }
    return null;
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
