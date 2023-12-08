package life.catalogue.resources;

import io.dropwizard.auth.Auth;

import io.swagger.v3.oas.annotations.Hidden;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.model.User;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.Redirect;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.printer.*;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Secret public services for xcol while it is still a private dataset
 */
@Path("/xcol")
@Hidden
@Deprecated
@Produces(MediaType.APPLICATION_JSON)
public class XColResource {
  private final ExportManager exportManager;
  private final WsServerConfig cfg;
  private final LatestDatasetKeyCache cache;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(XColResource.class);

  public XColResource(LatestDatasetKeyCache cache, ExportManager exportManager, WsServerConfig cfg) {
    this.exportManager = exportManager;
    this.cache = cache;
    this.cfg = cfg;
  }

  private ExportRequest buildXColRequest() {
    var req = new ExportRequest();
    req.setDatasetKey(cache.getLatestReleaseCandidate(Datasets.COL, true));
    req.setFormat(DataFormat.DWCA);
    req.setExtended(true);
    req.setForce(false);
    return req;
  }

  @POST
  @Path("export")
  @RolesAllowed({Roles.ADMIN})
  public UUID export(@Auth User user) {
    return exportManager.submit(buildXColRequest(), user.getKey());
  }

  @GET
  @Path("export")
  @VaryAccept
  // there are many unofficial mime types around for zip
  @Produces({
    MediaType.APPLICATION_OCTET_STREAM,
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  public Response download() {
    var req = buildXColRequest();
    UUID exportKey = exportManager.exists(req);
    if (exportKey != null) {
      return Redirect.temporary(cfg.job.downloadURI(exportKey));
    }

    throw new NotFoundException(req.getDatasetKey(), req.getFormat().getName() + " archive for dataset " + req.getDatasetKey() + " not found");
  }

}
