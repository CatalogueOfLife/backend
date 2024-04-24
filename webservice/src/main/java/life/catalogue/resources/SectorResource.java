package life.catalogue.resources;

import io.swagger.v3.oas.annotations.Hidden;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.assembly.SyncManager;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.matching.decision.RematcherBase;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.dropwizard.auth.Auth;

@Path("/dataset/{key}/sector")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorResource extends AbstractDatasetScopedResource<Integer, Sector, SectorSearchRequest> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorResource.class);
  private final SectorDao dao;
  private final SectorImportDao sid;
  private final FileMetricsSectorDao fmsDao;
  private final SyncManager assembly;

  public SectorResource(SectorDao dao, TaxonDao tdao, FileMetricsSectorDao fmsDao, SectorImportDao sid, SyncManager assembly) {
    super(Sector.class, dao);
    this.dao = dao;
    this.sid = sid;
    this.fmsDao = fmsDao;
    this.assembly = assembly;
  }

  @Override
  ResultPage<Sector> searchImpl(int datasetKey, SectorSearchRequest req, Page page) {
    if (req.isSubject()) {
      req.setSubjectDatasetKey(datasetKey);
    } else {
      req.setDatasetKey(datasetKey);
    }
    return dao.search(req, page);
  }

  @DELETE
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteByDataset(@PathParam("key") int catalogueKey,
                              @QueryParam("datasetKey") int datasetKey,
                              @QueryParam("full") boolean full,
                              @Context SqlSession session, @Auth User user) {
    SectorMapper sm = session.getMapper(SectorMapper.class);
    int counter = 0;
    for (Sector s : sm.listByDataset(catalogueKey, datasetKey)) {
      assembly.deleteSector(s, full, user);
      counter++;
    }
    LOG.info("Scheduled deletion of all {} sectors for dataset {} in catalogue {}", counter, datasetKey, catalogueKey);
  }

  @GET
  @Path("sync")
  public ResultPage<SectorImport> list(@PathParam("key") int datasetKey,
                                       @QueryParam("sectorKey") Integer sectorKey,
                                       @QueryParam("datasetKey") Integer subjectDatasetKey,
                                       @QueryParam("state") List<ImportState> states,
                                       @QueryParam("running") Boolean running,
                                       @Valid @BeanParam Page page,
                                       @Context SqlSession session) {
    if (running != null) {
      states = running ? ImportState.runningStates() : ImportState.finishedStates();
    }
    final List<ImportState> immutableStates = ImmutableList.copyOf(states);
    SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
    List<SectorImport> imports = sim.list(sectorKey, datasetKey, subjectDatasetKey, states, null, page);
    return new ResultPage<>(page, imports, () -> sim.count(sectorKey, datasetKey, subjectDatasetKey, immutableStates));
  }

  @POST
  @Path("sync")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void sync(@PathParam("key") int datasetKey, RequestScope request, @Auth User user) {
    DaoUtils.requireManaged(datasetKey);
    assembly.sync(datasetKey, request, user);
  }

  @DELETE
  @Path("sync/orphans")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") int datasetKey, @Auth User user) throws IOException {
    LOG.info("Remove orphaned sector imports and metrics from project {} by user {}", datasetKey, user.getName());
    sid.removeOrphanedImports(datasetKey);
  }

  @GET
  @Hidden
  @Path("{id}/scrutinizer")
  public Map<String, Integer> scrutinizer(@PathParam("key") int datasetKey, @PathParam("id") Integer id, @Context SqlSession session) {
    var sm = session.getMapper(SectorImportMapper.class);
    var list = sm.countTaxaByScrutinizer(datasetKey, id);
    return DatasetImportDao.countMap(list);
  }

  @DELETE
  @Override
  @Path("{id}")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") int datasetKey, @PathParam("id") Integer id, @Context UriInfo uri, @Auth User user) {
    // an asynchroneous sector deletion will be triggered which also removes catalogue data
    boolean full = Boolean.parseBoolean(uri.getQueryParameters().getFirst("full"));
    assembly.deleteSector(DSID.of(datasetKey, id), full, user);
  }

  @POST
  @Path("{id}/sync")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void runSync(@PathParam("key") int datasetKey, @PathParam("id") int id, @Auth User user) {
    RequestScope req = RequestScope.sector(DSID.of(datasetKey, id));
    sync(datasetKey, req, user);
  }

  @DELETE
  @Path("{id}/sync")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteSync(@PathParam("key") int datasetKey, @PathParam("id") int id, @Auth User user) {
    DaoUtils.requireManaged(datasetKey);
    assembly.cancel(DSID.of(datasetKey, id), user.getKey());
  }

  @GET
  @Path("{id}/sync")
  public SectorImport getLastSyncAttempt(@PathParam("key") int datasetKey, @PathParam("id") int id,
                                     @Context SqlSession session) {
    // a release? use mother project in that case
    // this also checks for presence & deletion of the dataset key
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    DSID<Integer> skey = DSID.of(datasetKey, id);
    Sector s = session.getMapper(SectorMapper.class).get(skey);
    if (s == null) {
      throw NotFoundException.notFound(Sector.class, skey);
    } else if (s.getSyncAttempt() == null) {
      throw new NotFoundException(skey, "Sector " + skey + " was never synced");
    }
    if (info.origin.isRelease()) {
      Integer projectKey = info.sourceKey;
      skey = DSID.of(projectKey, id);
    }

    return session.getMapper(SectorImportMapper.class).get(skey, s.getSyncAttempt());
  }

  @GET
  @Path("{id}/sync/{attempt}")
  public SectorImport getSyncAttempt(@PathParam("key") int datasetKey, @PathParam("id") int id,
                                       @PathParam("attempt") int attempt,
                                       @Context SqlSession session) {
    DaoUtils.requireManaged(datasetKey);
    return session.getMapper(SectorImportMapper.class).get(DSID.of(datasetKey, id), attempt);
  }

  @GET
  @Path("{id}/sync/{attempt}/names")
  @Produces({MediaType.TEXT_PLAIN})
  public Stream<String> getSyncAttemptNames(@PathParam("key") int datasetKey,
                                            @PathParam("id") int id,
                                            @PathParam("attempt") int attempt) {
    return fmsDao.getNames(DSID.of(datasetKey, id), attempt);
  }

  @POST
  @Path("/rematch")
  public RematcherBase.MatchCounter rematch(@PathParam("key") int projectKey, SectorRematchRequest req, @Auth User user) {
    req.setDatasetKey(projectKey);
    return SectorRematcher.match(dao, req, user.getKey());
  }

  @POST
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Path("/createFromPublisher")
  public int createFromPublisher(@PathParam("key") int projectKey, @QueryParam("publisherKey") UUID publisherKey, @QueryParam("ranks") Set<Rank> ranks, @Auth User user) throws Exception {
    return dao.createMissingMergeSectorsFromPublisher(projectKey, user.getKey(), ranks, publisherKey, Set.of());
  }

}
