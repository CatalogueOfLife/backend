package life.catalogue.resources;

import com.github.dockerjava.api.DockerClient;

import com.google.common.base.Preconditions;

import io.dropwizard.auth.Auth;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.User;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.matching.TaxonomicAlignJob;

import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;

@Path("/dataset/{key}/taxalign")
@SuppressWarnings("static-method")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetTaxDiffResource {
  private final JobExecutor exec;
  private final DockerClient docker;
  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;

  public DatasetTaxDiffResource(JobExecutor exec, SqlSessionFactory factory, DockerClient docker, WsServerConfig cfg) {
    this.cfg = cfg;
    this.exec = exec;
    this.docker = docker;
    this.factory = factory;
  }

  @POST
  @Path("{key2}")
  public BackgroundJob taxdiff(@PathParam("key") Integer key,
                               @PathParam("key2") Integer key2,
                               @QueryParam("root") String root,
                               @QueryParam("root2") String root2,
                               @Auth User user) throws IOException {
    var job = new TaxonomicAlignJob(user.getKey(), key, root, key2, root2, factory, docker, cfg.normalizer);
    exec.submit(job);
    return job;
  }

}
