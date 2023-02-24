package life.catalogue.resources;

import life.catalogue.api.model.User;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.util.List;
import java.util.UUID;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;

@Path("/job")
@Produces(MediaType.APPLICATION_JSON)
public class JobResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(JobResource.class);
  private final JobExecutor exec;
  private final JobConfig cfg;

  public JobResource(JobConfig cfg, JobExecutor executor) {
    this.cfg = cfg;
    this.exec = executor;
  }

  @GET
  public List<? extends BackgroundJob> jobQueue(@QueryParam("datasetKey") Integer datasetKey) {
    return datasetKey == null ? exec.getQueue() : exec.getQueueByDataset(datasetKey);
  }

  @GET
  @VaryAccept
  @Path("{key}")
  public BackgroundJob job(@PathParam("key") UUID key) {
    return exec.getJob(key);
  }

  @GET
  @VaryAccept
  @Path("{key}")
  // there are many unofficial mime types around for zip, support them all
  @Produces({
    MediaType.APPLICATION_OCTET_STREAM,
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  public Response redirectToDownloadFile(@PathParam("key") UUID key) {
    return Response.status(Response.Status.FOUND)
                   .location(cfg.downloadURI(key))
                   .header(MoreHttpHeaders.CONTENT_DISPOSITION, ResourceUtils.fileAttachment("result-" + key + ".zip"))
                   .build();
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN})
  public BackgroundJob cancel(@PathParam("key") UUID key, @Auth User user) {
    return exec.cancel(key, user.getKey());
  }
}
