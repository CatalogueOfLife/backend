package life.catalogue.resources;

import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.JobLane;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.JobDao;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.ClassPath;

import io.dropwizard.auth.Auth;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/job")
@Produces(MediaType.APPLICATION_JSON)
public class JobResource {

  private static final Logger LOG = LoggerFactory.getLogger(JobResource.class);
  private static final String JOB_PACKAGE = "life.catalogue";
  private final JobExecutor exec;
  private final JobDao dao;
  private final JobConfig cfg;
  private final List<String> jobTypes;

  public JobResource(JobConfig cfg, JobExecutor executor, JobDao dao) {
    this.cfg = cfg;
    this.exec = executor;
    this.dao = dao;
    this.jobTypes = scanJobTypes();
  }

  /**
   * Scans the classpath once at startup for all concrete background job implementations,
   * the same values that end up in the job_class column. Cheaper and far more stable than
   * a SELECT DISTINCT job_class over millions of history rows.
   *
   * Nested classes are included, not just top level ones: job_class holds the bare
   * getSimpleName() either way, so a job declared inside its factory - MatcherBuildJob in
   * UsageMatcherFactory, say - is persisted and searchable exactly like any other and must
   * be offered as a filter value too.
   */
  @VisibleForTesting
  static List<String> scanJobTypes() {
    List<String> types = new ArrayList<>();
    try {
      for (ClassPath.ClassInfo info : ClassPath.from(BackgroundJob.class.getClassLoader()).getAllClasses()) {
        if (!inJobPackage(info.getPackageName())) continue;
        try {
          Class<?> cl = info.load();
          if (BackgroundJob.class.isAssignableFrom(cl) && !Modifier.isAbstract(cl.getModifiers())
              // an anonymous or local job has no stable simple name to filter by - it is "" or
              // a digit prefixed one, and never a value anybody can pick from a list
              && !cl.isAnonymousClass() && !cl.isLocalClass() && !cl.isSynthetic()) {
            types.add(cl.getSimpleName());
          }
        } catch (Throwable e) {
          // optional dependencies can leave unloadable classes behind - they are never jobs
          LOG.debug("Cannot load class {} while scanning for job types", info.getName(), e);
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to scan for background job types", e);
    }
    // job_class only ever holds the simple name, so equal names from different packages are one type
    var sorted = types.stream().distinct().sorted().toList();
    LOG.info("Found {} background job types", sorted.size());
    return sorted;
  }

  private static boolean inJobPackage(String packageName) {
    return packageName.equals(JOB_PACKAGE) || packageName.startsWith(JOB_PACKAGE + ".");
  }

  /**
   * The live state of the job queues, served purely from executor memory so it can be polled frequently.
   * All four members describe the very same set of jobs, so the counts always match the arrays -
   * also when the queue was narrowed down by a dataset.
   */
  public static class JobQueueState {
    public final List<JobInfo> running;
    public final List<JobInfo> queued;
    public final Map<JobLane, Integer> queuedCounts;
    public final int queuedTotal;

    JobQueueState(List<BackgroundJob> jobs) {
      this.running = jobs.stream().filter(BackgroundJob::isRunning).map(JobDao::buildInfo).collect(Collectors.toList());
      // WAITING and BLOCKED alike - a job blocked on a dataset lock is queued from the outside and must
      // not fall out of both lists. Jobs that just ended can linger in a queue snapshot, those are dropped.
      var waiting = jobs.stream().filter(j -> j.getStatus().isQueued()).collect(Collectors.toList());
      this.queued = waiting.stream().map(JobDao::buildInfo).collect(Collectors.toList());
      // keep every lane in the map so the shape does not change with the load
      var counts = new EnumMap<JobLane, Integer>(JobLane.class);
      for (JobLane lane : JobLane.values()) {
        counts.put(lane, 0);
      }
      waiting.forEach(j -> counts.merge(j.getLane(), 1, Integer::sum));
      this.queuedCounts = counts;
      this.queuedTotal = waiting.size();
    }
  }

  /**
   * The live queue, rendered as the same generic JobInfo the history and single job lookups use.
   */
  @GET
  public JobQueueState jobQueue(@QueryParam("datasetKey") Integer datasetKey) {
    var jobs = datasetKey == null ? exec.getQueue() : exec.getQueue().stream()
      .filter(j -> datasetKey.equals(j.datasetKey()))
      .collect(Collectors.toList());
    return new JobQueueState(jobs);
  }

  /**
   * @return all known background job types, i.e. the values the job filter of the search accepts.
   */
  @GET
  @Path("types")
  public List<String> types() {
    return jobTypes;
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
  public JobInfo job(@PathParam("key") UUID key) {
    // live jobs first - the db record of a running job trails its in memory state
    BackgroundJob job = exec.getJob(key);
    return job != null ? JobDao.buildInfo(job) : dao.get(key);
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
  public JobInfo cancel(@PathParam("key") UUID key, @Auth User user) {
    BackgroundJob job = exec.getJob(key);
    if (job == null) {
      throw new NotFoundException("No running or queued job " + key);
    }
    if (job.getUserKey() != user.getKey() && !user.isAdmin()) {
      throw new ForbiddenException("Only the owner or an admin may cancel job " + key);
    }
    // the job can still finish between the lookup above and the cancel - fall back to its db record
    var canceled = exec.cancel(key, user.getKey());
    return canceled == null ? dao.get(key) : JobDao.buildInfo(canceled);
  }
}
