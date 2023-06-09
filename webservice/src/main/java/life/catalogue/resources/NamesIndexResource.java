package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.util.RegexUtils;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.importer.NameInterpreter;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexImpl;
import life.catalogue.matching.NameIndexMapDBStore;

import life.catalogue.matching.NidxExportJob;

import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Path("/nidx")
@Produces(MediaType.APPLICATION_JSON)
public class NamesIndexResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexResource.class);
  private final NameIndex ni;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings());
  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;
  private final JobExecutor exec;

  public NamesIndexResource(NameIndex ni, SqlSessionFactory factory, WsServerConfig cfg,JobExecutor exec) {
    this.ni = ni;
    this.factory = factory;
    this.cfg = cfg;
    this.exec = exec;
  }

  @GET
  @Path("metadata")
  public NidxMetadata getCreated() {
    return new NidxMetadata(ni.created(), ni.size());
  }

  public static class NidxMetadata {
    public final LocalDateTime created;
    public final int size;

    public NidxMetadata(LocalDateTime created, int size) {
      this.created = created;
      this.size = size;
    }
  }

  @GET
  @Path("{key}")
  public IndexName get(@PathParam("key") int key) {
    return ni.get(key);
  }

  @GET
  @Path("{key}/group")
  public Collection<IndexName> byCanonical(@PathParam("key") int key) {
    return ni.byCanonical(key);
  }

  @GET
  @Path("match")
  public NameMatch match(@QueryParam("q") String q,
                         @QueryParam("name") String name,
                         @QueryParam("scientificName") String sciname,
                         @QueryParam("authorship") String authorship,
                         @QueryParam("rank") Rank rank,
                         @QueryParam("code") NomCode code,
                         @QueryParam("verbose") boolean verbose) throws InterruptedException {
    SimpleNameClassified<SimpleName> sn = SimpleNameClassified.snc(null, rank, code, null, ObjectUtils.coalesce(sciname, name, q), authorship);
    Name n = interpreter.interpret(sn, IssueContainer.VOID)
                        .orElseThrow(() -> new IllegalArgumentException("Failed to interpret name")).getName();
    return ni.match(n, false, verbose);
  }

  @GET
  @Path("pattern")
  public List<IndexName> searchByRegex(@QueryParam("q") String regex,
                                       @QueryParam("canonical") @DefaultValue("true") boolean canonical,
                                       @QueryParam("rank") Rank rank,
                                       @Valid @BeanParam Page page,
                                       @Context SqlSession session) {
    RegexUtils.validatePattern(regex);
    Page p = page == null ? new Page() : page;
    return session.getMapper(NamesIndexMapper.class).listByRegex(regex, canonical, rank, p);
  }

  @POST
  @Path("export")
  public NidxExportJob export(@QueryParam("datasetKey") List<Integer> keys, @QueryParam("min") int minDatasets, @Auth User user) {
    NidxExportJob job = new NidxExportJob(keys, minDatasets, user.getKey(), factory, cfg);
    exec.submit(job);
    return job;
  }

  @POST
  @Hidden
  @Path("compact")
  @RolesAllowed({Roles.ADMIN})
  public void compact() {
    store().compact();
  }

  @GET
  @Hidden
  @Path("debug/{key}")
  @RolesAllowed({Roles.ADMIN})
  public int[] debugCanonical(@PathParam("key") int key) {
    return store().debugCanonical(key);
  }

  private NameIndexMapDBStore store() {
    return ((NameIndexImpl) ni).store();
  }

}
