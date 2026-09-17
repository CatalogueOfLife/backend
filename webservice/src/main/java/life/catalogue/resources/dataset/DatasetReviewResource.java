package life.catalogue.resources.dataset;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.User;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.JobDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.JwtCodec;
import life.catalogue.dw.auth.Roles;
import life.catalogue.release.review.AiReviewConfig;
import life.catalogue.release.review.ReleaseReviewInfo;
import life.catalogue.release.review.ReleaseReviewJob;
import life.catalogue.release.review.ReleaseReviewStore;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The review of a release: its status, the report it produced, and the trigger that requests one.
 *
 * Releases and extended releases only - there is nothing to review about a project, which changes under you,
 * or about an external dataset, which has no previous release of its own kind.
 *
 * There is no table behind this. The status is derived from the files in the release report directory, which
 * means it survives a redeploy, is published by the download host as-is, and disappears with the release.
 * Read access is the ordinary dataset rule (PrivateFilter/AuthFilter evaluate a release by its project), so a
 * project's editors and reviewers can see the review of a private draft release.
 */
@Path("/dataset/{key}/review")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetReviewResource {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetReviewResource.class);

  private final SqlSessionFactory factory;
  private final ReleaseConfig rCfg;
  private final ReleaseReviewStore store;
  private final JobExecutor exec;
  private final JwtCodec jwt;
  private final @Nullable AiReviewConfig aiCfg;
  private final CloseableHttpClient http;

  public DatasetReviewResource(SqlSessionFactory factory, ReleaseConfig rCfg, JobExecutor exec, JwtCodec jwt,
                               @Nullable AiReviewConfig aiCfg, CloseableHttpClient http) {
    this.factory = factory;
    this.rCfg = rCfg;
    this.store = new ReleaseReviewStore(rCfg);
    this.exec = exec;
    this.jwt = jwt;
    this.aiCfg = aiCfg;
    this.http = http;
  }

  @GET
  public ReleaseReviewInfo get(@PathParam("key") int key) {
    try (SqlSession session = factory.openSession()) {
      return build(session, requireRelease(session, key));
    }
  }

  /**
   * Requests a new AI review of this release.
   *
   * @param force redo a release that has a report already. Admins only - a second run costs real money and
   *              overwrites a report someone may already have linked to.
   * @return the submitted job, in the same generic shape every other job submitting endpoint answers with
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public JobInfo request(@PathParam("key") int key,
                         @QueryParam("force") @DefaultValue("false") boolean force,
                         @Auth User user) {
    if (aiCfg == null) {
      throw new ServiceUnavailableException("No AI review configured on this server");
    }
    if (force && !user.isAdmin()) {
      throw new ForbiddenException("Only admins can redo an existing review");
    }

    final ReleaseReviewInfo info;
    final User bot;
    try (SqlSession session = factory.openSession()) {
      var release = requireRelease(session, key);
      info = build(session, release);
      bot = session.getMapper(UserMapper.class).getByUsername(aiCfg.botUser);
    }
    if (info.getPreviousReleaseKey() == null) {
      throw new IllegalArgumentException("Release " + key + " has no previous public release of the same kind to compare against");
    }
    if (bot == null) {
      throw new ServiceUnavailableException("The configured review bot user does not exist");
    }
    if (!force && info.getStatus() == ReleaseReviewInfo.Status.FINISHED) {
      // the existing report is linked from GET, so a second review is a deliberate admin act, not a retry
      throw conflict("Release " + key + " has been reviewed already");
    }
    if (isQueued(key)) {
      throw conflict("A review of release " + key + " is running already");
    }

    // a short lived token for the read-only bot, handed to the job and never stored anywhere
    final String token = jwt.generate(bot);
    var job = new ReleaseReviewJob(user.getKey(), key, force, token, aiCfg, rCfg, factory, http);
    exec.submit(job);
    LOG.info("Requested AI review of release {} by user {}", key, user.getKey());
    return JobDao.buildInfo(job);
  }

  private static ClientErrorException conflict(String msg) {
    return new ClientErrorException(msg, Response.Status.CONFLICT);
  }

  /**
   * The mirror image of DaoUtils.requireProject: a review only exists for a release.
   */
  private static Dataset requireRelease(SqlSession session, int key) {
    // fails fast on an unknown or deleted key
    var cached = DatasetInfoCache.CACHE.info(key);
    if (!cached.origin.isRelease()) {
      throw new IllegalArgumentException("Only releases can be reviewed. Dataset " + key + " is of origin " + cached.origin);
    }
    var d = session.getMapper(DatasetMapper.class).get(key);
    if (d == null) {
      throw NotFoundException.notFound(Dataset.class, key);
    }
    return d;
  }

  /**
   * Composes the answer from the release itself and the sidecar on disk. The release derived properties are
   * always recomputed rather than read back from the sidecar, so a review written before another release was
   * published cannot report a stale previousReleaseKey.
   */
  private ReleaseReviewInfo build(SqlSession session, Dataset release) {
    final int key = release.getKey();
    final int projectKey = release.getSourceKey();
    final Integer attempt = release.getAttempt();

    ReleaseReviewInfo info = attempt == null ? null : store.read(projectKey, attempt);
    if (info == null) {
      info = new ReleaseReviewInfo();
      info.setStatus(ReleaseReviewInfo.Status.NONE);
    }
    info.setReleaseKey(key);
    info.setProjectKey(projectKey);
    info.setOrigin(release.getOrigin());
    info.setAttempt(attempt);
    info.setPreviousReleaseKey(session.getMapper(DatasetMapper.class).previousRelease(key));

    if (attempt != null && store.hasReport(projectKey, attempt)) {
      // the report file is the truth: a sidecar left behind as RUNNING by a killed server must not hide a
      // report that is actually there
      info.setStatus(ReleaseReviewInfo.Status.FINISHED);
      info.setReportURI(store.reportURI(projectKey, attempt));
    } else {
      info.setReportURI(null);
      if (info.getStatus() == ReleaseReviewInfo.Status.FINISHED) {
        // finished according to the sidecar, but the report is gone - e.g. the reports were cleaned up
        info.setStatus(ReleaseReviewInfo.Status.NONE);
      }
      if (info.getStatus() == ReleaseReviewInfo.Status.RUNNING && !isQueued(key)) {
        // nothing is running any more, so the sidecar outlived its job - a server restart above all
        info.setStatus(ReleaseReviewInfo.Status.FAILED);
        if (info.getError() == null) {
          info.setError("The review job did not survive a server restart");
        }
      }
    }
    return info;
  }

  private boolean isQueued(int releaseKey) {
    return exec.getQueueByJobClass(ReleaseReviewJob.class).stream()
      .anyMatch(j -> j.getReleaseKey() == releaseKey);
  }
}
