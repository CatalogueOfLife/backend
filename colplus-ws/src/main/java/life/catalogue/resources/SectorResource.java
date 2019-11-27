package life.catalogue.resources;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.tree.DiffService;
import life.catalogue.db.tree.NamesDiff;
import life.catalogue.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/sector")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorResource extends AbstractDecisionResource<Sector> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorResource.class);
  private final DatasetImportDao diDao;
  private final DiffService diff;
  private final AssemblyCoordinator assembly;
  
  public SectorResource(SqlSessionFactory factory, DatasetImportDao diDao, DiffService diffService, AssemblyCoordinator assembly) {
    super(Sector.class, new SectorDao(factory), factory);
    this.diDao = diDao;
    this.diff = diffService;
    this.assembly = assembly;
  }
  
  @DELETE
  @Override
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @Auth ColUser user) {
    // an asynchroneous sector deletion will be triggered which also removes catalogue data
    assembly.deleteSector(key, user);
  }
  
  @DELETE
  @Path("dataset/{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteDatasetSectors(@PathParam("datasetKey") int datasetKey, @Context SqlSession session, @Auth ColUser user) {
    SectorMapper sm = session.getMapper(SectorMapper.class);
    int counter = 0;
    for (Sector s : sm.listByDataset(Datasets.DRAFT_COL, datasetKey)) {
      assembly.deleteSector(s.getKey(), user);
      counter++;
    }
    LOG.info("Scheduled deletion of all {} draft sectors for dataset {}", counter, datasetKey);
  }
  
  @GET
  public List<Sector> list(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey) {
    return session.getMapper(SectorMapper.class).listByDataset(Datasets.DRAFT_COL, datasetKey);
  }
  
  @GET
  @Path("/broken")
  public List<Sector> broken(@Context SqlSession session,
                             @QueryParam("target") boolean target,
                             @NotNull @QueryParam("datasetKey") Integer datasetKey) {
    SectorMapper mapper = session.getMapper(SectorMapper.class);
    if (target) {
      return mapper.targetBroken(Datasets.DRAFT_COL, datasetKey);
    } else {
      return mapper.subjectBroken(Datasets.DRAFT_COL, datasetKey);
    }
  }
  
  @GET
  @Path("{key}/sync")
  public ResultPage<SectorImport> list(@PathParam("key") int key,
                                       @QueryParam("state") List<SectorImport.State> states,
                                       @QueryParam("running") Boolean running,
                                       @Valid @BeanParam Page page,
                                       @Context SqlSession session) {
    if (running != null) {
      states = running ? SectorImport.runningStates() : SectorImport.finishedStates();
    }
    final List<SectorImport.State> immutableStates = ImmutableList.copyOf(states);
    SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
    List<SectorImport> imports = sim.list(key, Datasets.DRAFT_COL, null, states, page);
    return new ResultPage<>(page, imports, () -> sim.count(key, Datasets.DRAFT_COL, null, immutableStates));
  }
  
  @GET
  @Path("{key}/import/{attempt}/tree")
  public Stream<String> getImportAttemptTree(@PathParam("key") int key,
                                             @PathParam("attempt") int attempt) throws IOException {
    return diDao.getTreeDao().getSectorTree(key, attempt);
  }
  
  @GET
  @Path("{key}/import/{attempt}/names")
  public Stream<String> getImportAttemptNames(@PathParam("key") int key,
                                              @PathParam("attempt") int attempt) {
    return diDao.getTreeDao().getSectorNames(key, attempt);
  }
  
  @GET
  @Path("{key}/treediff")
  public Reader diffTree(@PathParam("key") int sectorKey,
                         @QueryParam("attempts") String attempts,
                         @Context SqlSession session) throws IOException {
    return diff.sectorTreeDiff(sectorKey, attempts);
  }
  
  @GET
  @Path("{key}/namesdiff")
  public NamesDiff diffNames(@PathParam("key") int sectorKey,
                             @QueryParam("attempts") String attempts,
                             @Context SqlSession session) throws IOException {
    return diff.sectorNamesDiff(sectorKey, attempts);
  }
}
