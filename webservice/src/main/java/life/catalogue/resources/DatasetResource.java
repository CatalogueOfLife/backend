package life.catalogue.resources;

import life.catalogue.jobs.ValidationJob;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.assembly.SyncManager;
import life.catalogue.assembly.SyncState;
import life.catalogue.basgroup.HomotypicConsolidationJob;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.job.DeleteDatasetJob;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.release.ProjectCopyFactory;
import life.catalogue.release.ProjectRelease;

import java.util.*;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import life.catalogue.release.ProjectReleaseConfig;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.base.Preconditions;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;

import static life.catalogue.api.model.User.userkey;

@Path("/dataset")
@SuppressWarnings("static-method")
public class DatasetResource extends AbstractGlobalResource<Dataset> {
  private final DatasetDao dao;
  private final JobExecutor exec;

  public DatasetResource(SqlSessionFactory factory, JobExecutor exec, DatasetDao dao) {
    super(Dataset.class, dao, factory);
    this.exec = exec;
    this.dao = dao;
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML})
  public Integer createAlt(Dataset obj, @Auth User user) {
    return this.create(obj, user);
  }

  /**
   * Require only an authenticated user to create new datasets
   */
  @POST
  @Override
  public Integer create(Dataset obj, @Auth User user) {
    return super.create(obj, user);
  }

  @GET
  @VaryAccept
  public ResultPage<Dataset> search(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req, @Auth Optional<User> user) {
    return dao.search(req, userkey(user), page);
  }

  @GET
  @Path("keys")
  public List<Integer> listAllKeys(@BeanParam DatasetSearchRequest req) {
    return dao.searchKeys(req);
  }

  @GET
  @Path("duplicates")
  public List<Duplicate.IntKeys> listDuplicates(@QueryParam("minCount") @DefaultValue("2") int minCount,  @QueryParam("gbifPublisherKey") UUID gbifPublisherKey) {
    Preconditions.checkArgument(minCount>1, "minCount parameter must be greater than 1");
    return dao.listDuplicates(minCount, gbifPublisherKey);
  }

  @GET
  @Path("{key}")
  @Override
  @VaryAccept
  @Produces({MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML,
    MoreMediaTypes.APP_JSON_CSL,
    MoreMediaTypes.APP_BIBTEX
  })
  public Dataset get(@PathParam("key") Integer key) {
    return super.get(key);
  }

  @PUT
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML})
  public void updateAlt(@PathParam("key") Integer key, Dataset obj, @Auth User user) {
    // merge metadata?
    Dataset old;
    try (SqlSession session = factory.openSession(true)){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      var settings = dm.getSettings(key);
      old = dm.get(key);
      if (settings == null || old == null) {
        throw NotFoundException.notFound(Dataset.class, key);
      }
      dao.patchMetadata(new DatasetWithSettings(old,settings), obj);
    }
    this.update(key, old, user);
  }


  @Override
  public void delete(Integer key, boolean async, User user) {
    if (async) {
      // the constructor already makes sure the dataset exists
      var job = new DeleteDatasetJob(key, user.getKey(), dao);
      exec.submit(job);
    } else {
      super.delete(key, async, user);
    }
  }

  @PUT
  @Path("{key}/publish")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public boolean publish(@PathParam("key") int key, @Auth User user) {
    return dao.publish(key, user);
  }

  @GET
  @Path("{key}/{attempt}")
  @VaryAccept
  @Produces({MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML,
    MoreMediaTypes.APP_JSON_CSL,
    MoreMediaTypes.APP_BIBTEX
  })
  public Dataset getMetadataArchive(@PathParam("key") Integer key, @PathParam("attempt") Integer attempt) {
    return dao.getArchive(key, attempt);
  }

  @GET
  @Path("{key}/settings")
  public DatasetSettings getSettings(@PathParam("key") int key) {
    return dao.getSettings(key);
  }

  @PUT
  @Path("{key}/settings")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void putSettings(@PathParam("key") int key, DatasetSettings settings, @Auth User user) {
    dao.putSettings(key, settings, user.getKey());
  }

  @GET
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Path("{key}/matches/count")
  public int count(@PathParam("key") int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(NameMatchMapper.class).countByDataset(datasetKey);
    }
  }

  @GET
  @Hidden
  @Path("{key}/scrutinizer")
  public Map<String, Integer> scrutinizer(@PathParam("key") int key, @Context SqlSession session) {
    var dim = session.getMapper(DatasetImportMapper.class);
    var list = dim.countTaxaByScrutinizer(key);
    return DatasetImportDao.countMap(list);
  }
}
