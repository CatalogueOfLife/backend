package life.catalogue.resources.dataset;

import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.SyncManager;
import life.catalogue.assembly.SyncState;
import life.catalogue.basgroup.HomotypicConsolidationJob;
import life.catalogue.basgroup.NameRelationCleanerJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.JobDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.jobs.ProjectValidationJob;
import life.catalogue.release.ProjectCopyFactory;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/dataset")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DatasetJobResource {
  private final DatasetDao dao;
  private final SyncManager assembly;
  private final JobExecutor exec;
  private final ProjectCopyFactory jobFactory;
  private final SqlSessionFactory factory;

  public DatasetJobResource(SqlSessionFactory factory, DatasetDao dao, SyncManager assembly, ProjectCopyFactory jobFactory, JobExecutor exec) {
    this.factory = factory;
    this.dao = dao;
    this.assembly = assembly;
    this.jobFactory = jobFactory;
    this.exec = exec;
  }

  @GET
  @Path("{key}/assembly")
  public SyncState assemblyState(@PathParam("key") int key) {
    return assembly.getState(key);
  }

  @POST
  @Path("{key}/copy")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void copy(@PathParam("key") int key, @Auth User user) {
    var job = jobFactory.buildDuplication(key, user.getKey());
    exec.submit(job);
  }

  /**
   * Groups homotypic names, entirely or below a root taxon.
   * The name relations are cleaned up first unless cleanRelations is false, as in an extended release.
   */
  @POST
  @Path("{key}/consolidate-homotypic")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public JobInfo homotypicGrouping(@PathParam("key") int key,
                                   @QueryParam("taxonID") String taxonID,
                                   @QueryParam("cleanRelations") @DefaultValue("true") boolean cleanRelations,
                                   @Auth User user) {
    var job = new HomotypicConsolidationJob(factory, key, user.getKey(), StringUtils.trimToNull(taxonID), cleanRelations);
    exec.submit(job);
    return JobDao.buildInfo(job);
  }

  /**
   * Removes name relations that contradict their names or each other, entirely or below a root taxon,
   * as an extended release does before grouping homotypic names.
   */
  @POST
  @Path("{key}/clean-name-relations")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public JobInfo cleanNameRelations(@PathParam("key") int key, @QueryParam("taxonID") String taxonID, @Auth User user) {
    var job = new NameRelationCleanerJob(factory, key, user.getKey(), StringUtils.trimToNull(taxonID));
    exec.submit(job);
    return JobDao.buildInfo(job);
  }

  @POST
  @Path("{key}/validate")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void validate(@PathParam("key") int key, @Auth User user) {
    exec.submit(new ProjectValidationJob(user.getKey(), factory, dao.getIndexService(), key));
  }

  @POST
  @Path("{key}/release")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void release(@PathParam("key") int key, @QueryParam("force") boolean force, @Auth User user) {
    var job = jobFactory.buildRelease(key, user.getKey(), force);
    exec.submit(job);
  }

  @POST
  @Path("{key}/xrelease")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public UUID xRelease(@PathParam("key") int key, @QueryParam("force") boolean force, @Auth User user) {
    Integer baseReleaseKey;
    try (SqlSession session = factory.openSession(true)) {
      baseReleaseKey = session.getMapper(DatasetMapper.class).latestRelease(key, true, DatasetOrigin.RELEASE);
    }
    if (baseReleaseKey == null) {
      throw new IllegalArgumentException("Project " + key + " was never released in public");
    }
    var job = jobFactory.buildExtendedRelease(baseReleaseKey, user.getKey(), force);
    exec.submit(job);
    return job.getKey();
  }

  @POST
  @Hidden
  @Path("{key}/xrdebug")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN})
  public UUID xrDebug(@PathParam("key") int key, @Auth User user) {
    Integer releaseKey;
    try (SqlSession session = factory.openSession(true)) {
      releaseKey = session.getMapper(DatasetMapper.class).latestRelease(key, true, DatasetOrigin.RELEASE);
    }
    if (releaseKey == null) {
      throw new IllegalArgumentException("Project " + key + " was never released in public");
    }
    var job = jobFactory.buildDebugXRelease(releaseKey, user.getKey());
    exec.submit(job);
    return job.getKey();
  }

}
