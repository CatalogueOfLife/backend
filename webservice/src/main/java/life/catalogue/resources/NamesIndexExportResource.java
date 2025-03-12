package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.util.RegexUtils;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.NidxExportJob;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexStore;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Path("/nidx/export")
@Produces(MediaType.APPLICATION_JSON)
public class NamesIndexExportResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexExportResource.class);
  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;
  private final JobExecutor exec;

  public NamesIndexExportResource(SqlSessionFactory factory, WsServerConfig cfg, JobExecutor exec) {
    this.factory = factory;
    this.cfg = cfg;
    this.exec = exec;
  }

  @POST
  @Path("export")
  public NidxExportJob export(@QueryParam("datasetKey") List<Integer> keys, @QueryParam("min") int minDatasets, @Auth User user) {
    NidxExportJob job = new NidxExportJob(keys, minDatasets, user.getKey(), factory, cfg.normalizer);
    exec.submit(job);
    return job;
  }
}
