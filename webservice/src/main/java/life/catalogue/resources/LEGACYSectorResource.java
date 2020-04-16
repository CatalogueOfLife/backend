package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.User;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.tree.DiffService;
import life.catalogue.db.tree.NamesDiff;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.Reader;
import java.util.stream.Stream;

@Path("/sector")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class LEGACYSectorResource extends LEGACYAbstractDecisionResource<Sector> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(LEGACYSectorResource.class);
  private final DatasetImportDao diDao;
  private final DiffService diff;
  private final AssemblyCoordinator assembly;
  private final SectorDao dao;

  public LEGACYSectorResource(SqlSessionFactory factory, SectorDao dao, DatasetImportDao diDao, DiffService diffService, AssemblyCoordinator assembly) {
    super(Sector.class, dao, factory);
    this.diDao = diDao;
    this.diff = diffService;
    this.assembly = assembly;
    this.dao = dao;
  }

  @GET
  public ResultPage<Sector> search(@Valid @BeanParam Page page, @BeanParam SectorSearchRequest req) {
    return dao.search(req, page);
  }

  @DELETE
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteByDataset(@QueryParam("datasetKey") int datasetKey,
                                   @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                   @Context SqlSession session, @Auth User user) {
    SectorMapper sm = session.getMapper(SectorMapper.class);
    int counter = 0;
    for (Sector s : sm.listByDataset(catalogueKey, datasetKey)) {
      assembly.deleteSector(s.getId(), user);
      counter++;
    }
    LOG.info("Scheduled deletion of all {} sectors for dataset {} in catalogue {}", counter, datasetKey, catalogueKey);
  }

  @DELETE
  @Override
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @Auth User user) {
    // an asynchroneous sector deletion will be triggered which also removes catalogue data
    assembly.deleteSector(key, user);
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
