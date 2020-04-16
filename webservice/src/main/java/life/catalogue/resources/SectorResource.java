package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.search.EstimateSearchRequest;
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
import org.checkerframework.checker.units.qual.K;
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

@Path("/dataset/{datasetKey}/sector")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorResource extends AbstractDatasetScopedResource<Integer, Sector, SectorSearchRequest> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorResource.class);
  private final DatasetImportDao diDao;
  private final DiffService diff;
  private final AssemblyCoordinator assembly;
  private final SectorDao dao;
  
  public SectorResource(SectorDao dao, DatasetImportDao diDao, DiffService diffService, AssemblyCoordinator assembly) {
    super(Sector.class, dao);
    this.diDao = diDao;
    this.diff = diffService;
    this.assembly = assembly;
    this.dao = dao;
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
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteByDataset(@QueryParam("datasetKey") int datasetKey,
                              @PathParam("datasetKey") int catalogueKey,
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
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") int datasetKey, @PathParam("id") Integer key, @Auth User user) {
    // an asynchroneous sector deletion will be triggered which also removes catalogue data
    assembly.deleteSector(key, user);
  }
  
  @GET
  @Path("{id}/import/{attempt}/tree")
  public Stream<String> getImportAttemptTree(@PathParam("id") int key,
                                             @PathParam("attempt") int attempt) throws IOException {
    return diDao.getTreeDao().getSectorTree(key, attempt);
  }
  
  @GET
  @Path("{id}/import/{attempt}/names")
  public Stream<String> getImportAttemptNames(@PathParam("id") int key,
                                              @PathParam("attempt") int attempt) {
    return diDao.getTreeDao().getSectorNames(key, attempt);
  }
  
  @GET
  @Path("{id}/treediff")
  public Reader diffTree(@PathParam("id") int sectorKey,
                         @QueryParam("attempts") String attempts,
                         @Context SqlSession session) throws IOException {
    return diff.sectorTreeDiff(sectorKey, attempts);
  }
  
  @GET
  @Path("{id}/namesdiff")
  public NamesDiff diffNames(@PathParam("id") int sectorKey,
                             @QueryParam("attempts") String attempts,
                             @Context SqlSession session) throws IOException {
    return diff.sectorNamesDiff(sectorKey, attempts);
  }
}
