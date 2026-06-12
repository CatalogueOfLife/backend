package life.catalogue.resources;

import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.JobLane;
import life.catalogue.dao.JobDao;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/job")
@Produces(MediaType.APPLICATION_JSON)
public class JobResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(JobResource.class);
  private final JobExecutor exec;
  private final JobDao dao;
  private final JobConfig cfg;

  public JobResource(JobConfig cfg, JobExecutor executor, JobDao dao) {
    this.cfg = cfg;
    this.exec = executor;
    this.dao = dao;
  }

  /**
   * The live state of the job queues, served purely from executor memory so it can be polled frequently.
   */
  public static class JobQueueState {
    public final List<BackgroundJob> running;
    public final List<BackgroundJob> queued;
    public final Map<JobLane, Integer> queuedCounts;
    public final int queuedTotal;

    JobQueueState(List<BackgroundJob> jobs, Map<JobLane, Integer> queuedCounts) {
      this.running = jobs.stream().filter(BackgroundJob::isRunning).collect(Collectors.toList());
      this.queued = jobs.stream().filter(BackgroundJob::isQueued).collect(Collectors.toList());
      this.queuedCounts = queuedCounts;
      this.queuedTotal = queuedCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
  }

  @GET
  public JobQueueState jobQueue(@QueryParam("datasetKey") Integer datasetKey) {
    var jobs = datasetKey == null ? exec.getQueue() : exec.getQueue().stream()
      .filter(j -> datasetKey.equals(j.datasetKey()))
      .collect(Collectors.toList());
    return new JobQueueState(jobs, exec.queueSizes());
  }

  /**
   * Searches the persisted job history in the database, including waiting, running and finished jobs of any kind.
   */
  @GET
  @Path("search")
  public ResultPage<JobInfo> search(@BeanParam JobSearchRequest req, @Valid @BeanParam Page page) {
    return dao.search(req, page);
  }

  @GET
  @VaryAccept
  @Path("{key}")
  public Object job(@PathParam("key") UUID key) {
    // live jobs first - they carry the full request and metrics details
    BackgroundJob job = exec.getJob(key);
    if (job != null) {
      return job;
    }
    return dao.get(key);
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

  @GET
  @VaryAccept
  @Path("{key}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response redirectToLogFile(@PathParam("key") UUID key) {
    return Response.status(Response.Status.FOUND)
        .location(cfg.logURI(key))
        .header(MoreHttpHeaders.CONTENT_DISPOSITION, ResourceUtils.fileAttachment("job-" + key + ".log.gz"))
        .build();
  }

  @DELETE
  @Path("{key}")
  public BackgroundJob cancel(@PathParam("key") UUID key, @Auth User user) {
    BackgroundJob job = exec.getJob(key);
    if (job == null) {
      throw new NotFoundException("No running or queued job " + key);
    }
    if (job.getUserKey() != user.getKey() && !user.isAdmin()) {
      throw new ForbiddenException("Only the owner or an admin may cancel job " + key);
    }
    return exec.cancel(key, user.getKey());
  }
}
