package life.catalogue.resources.dataset;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.User;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.matching.TaxonomicAlignJob;

import java.io.IOException;

import org.apache.ibatis.session.SqlSessionFactory;

import com.github.dockerjava.api.DockerClient;

import io.dropwizard.auth.Auth;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

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
