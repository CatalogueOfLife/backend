package life.catalogue.resources.dataset;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.SyncManager;
import life.catalogue.assembly.SyncState;
import life.catalogue.basgroup.HomotypicConsolidationJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.jobs.ProjectValidationJob;
import life.catalogue.release.ProjectCopyFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;

@Path("/dataset")
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

  @POST
  @Path("{key}/consolidate-homotypic")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void homotypicGrouping(@PathParam("key") int key, @QueryParam("taxonID") String taxonID, @Auth User user) {
    HomotypicConsolidationJob job;
    if (StringUtils.isBlank(taxonID)) {
      job = new HomotypicConsolidationJob(factory, key, user.getKey());
    } else {
      job = new HomotypicConsolidationJob(factory, key, user.getKey(), taxonID);
    }
    exec.submit(job);
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
  public void release(@PathParam("key") int key, @Auth User user) {
    var job = jobFactory.buildRelease(key, user.getKey());
    exec.submit(job);
  }

  @POST
  @Path("{key}/xrelease")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void xRelease(@PathParam("key") int key, @Auth User user) {
    Integer releaseKey;
    try (SqlSession session = factory.openSession(true)) {
      releaseKey = session.getMapper(DatasetMapper.class).latestRelease(key, true, DatasetOrigin.RELEASE);
    }
    if (releaseKey == null) {
      throw new IllegalArgumentException("Project " + key + " was never released in public");
    }
    var job = jobFactory.buildExtendedRelease(releaseKey, user.getKey());
    exec.submit(job);
  }

  @POST
  @Path("{key}/xrdebug")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void xrDebug(@PathParam("key") int key, @Auth User user) {
    Integer releaseKey;
    try (SqlSession session = factory.openSession(true)) {
      releaseKey = session.getMapper(DatasetMapper.class).latestRelease(key, true, DatasetOrigin.RELEASE);
    }
    if (releaseKey == null) {
      throw new IllegalArgumentException("Project " + key + " was never released in public");
    }
    var job = jobFactory.buildDebugXRelease(releaseKey, user.getKey());
    exec.submit(job);
  }

  @POST
  @Path("{key}/xrcontinue")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void xrContinue(@PathParam("key") int key, @QueryParam("tmpKey") int tmpKey, @Auth User user) {
    Integer releaseKey;
    try (SqlSession session = factory.openSession(true)) {
      releaseKey = session.getMapper(DatasetMapper.class).latestRelease(key, true, DatasetOrigin.RELEASE);
    }
    if (releaseKey == null) {
      throw new IllegalArgumentException("Project " + key + " was never released in public");
    }
    var job = jobFactory.buildContinueXRelease(releaseKey, tmpKey, user.getKey());
    exec.submit(job);
  }
}
