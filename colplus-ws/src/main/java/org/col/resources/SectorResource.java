package org.col.resources;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.Sector;
import org.col.assembly.AssemblyCoordinator;
import org.col.dao.DatasetImportDao;
import org.col.dao.SectorDao;
import org.col.db.mapper.SectorMapper;
import org.col.db.tree.DiffService;
import org.col.db.tree.NamesDiff;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/sector")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorResource extends GlobalEntityResource<Sector> {
  
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
  public void delete(Integer key, @Auth ColUser user) {
    // an asynchroneous sector deletion will be triggered which also removes catalogue data
    assembly.deleteSector(key, user);
  }
  
  @GET
  public List<Sector> list(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey) {
    return session.getMapper(SectorMapper.class).listByDataset(datasetKey);
  }
  
  @GET
  @Path("/broken")
  public List<Sector> broken(@Context SqlSession session,
                             @QueryParam("target") boolean target,
                             @NotNull @QueryParam("datasetKey") Integer datasetKey) {
    SectorMapper mapper = session.getMapper(SectorMapper.class);
    if (target) {
      return mapper.targetBroken(datasetKey);
    } else {
      return mapper.subjectBroken(datasetKey);
    }
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
