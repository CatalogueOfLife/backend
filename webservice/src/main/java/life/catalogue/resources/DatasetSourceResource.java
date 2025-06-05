package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dataset/{key}/source")
@SuppressWarnings("static-method")
public class DatasetSourceResource {
  private final DatasetSourceDao sourceDao;
  private final SqlSessionFactory factory;

  public DatasetSourceResource(SqlSessionFactory factory, DatasetSourceDao sourceDao) {
    this.factory = factory;
    this.sourceDao = sourceDao;
  }

  @GET
  public List<Dataset> projectOrReleaseSources(@PathParam("key") int datasetKey,
                                               @QueryParam("inclPublisherSources") boolean inclPublisherSources,
                                               @QueryParam("notCurrentOnly") boolean notCurrentOnly
  ) {
    var ds = sourceDao.listSimple(datasetKey, inclPublisherSources);
    if (notCurrentOnly) {
      List<Dataset> notCurrent = new ArrayList<>();
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        for (Dataset d : ds) {
          Integer currAttempt = dm.lastImportAttempt(d.getKey());
          if (currAttempt != null && !Objects.equals(currAttempt, d.getAttempt())) {
            notCurrent.add(d);
          }
        }
      }
      return notCurrent;
    }
    return ds;
  }

  @GET
  @Path("/suggest")
  public List<DatasetSimple> projectOrReleaseSourceSuggest(@PathParam("key") int datasetKey,
                                                           @QueryParam("merge") boolean inclMerge,
                                                           @QueryParam("q") String query
  ) {
    return sourceDao.suggest(datasetKey, query, inclMerge);
  }

  @GET
  @Path("/{id}")
  @VaryAccept
  @Produces({MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML,
    MoreMediaTypes.APP_JSON_CSL,
    MoreMediaTypes.APP_BIBTEX
  })
  public Dataset projectSource(@PathParam("key") int datasetKey,
                               @PathParam("id") int id,
                               @QueryParam("original") boolean original) {
    return sourceDao.get(datasetKey, id, original);
  }

  @PUT
  @Path("/{id}")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Consumes({MediaType.APPLICATION_JSON, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
  public void updateProjectSource(@PathParam("key") int datasetKey, @PathParam("id") int id, Dataset obj, @Auth User user) {
    if (obj==null) {
      throw new IllegalArgumentException("No source entity given for key " + id);
    }
    obj.setKey(id);
    obj.applyUser(user);
    int i = sourceDao.update(datasetKey, obj, user.getKey());
    if (i == 0) {
      throw NotFoundException.notFound(Dataset.class, DSID.of(datasetKey, id));
    }
  }

  @GET
  @Hidden
  @Path("/{id}/seo")
  @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML})
  public Response getHtmlHeader(@PathParam("key") int datasetKey, @PathParam("id") int id) {
    var d = sourceDao.get(datasetKey, id, false);
    if (d == null) {
      throw NotFoundException.notFound(Dataset.class, DSID.of(datasetKey, id));
    }
    return ResourceUtils.streamFreemarker(d, "seo/DATASET.ftl", MediaType.TEXT_PLAIN_TYPE);
  }

  @GET
  @Path("/{id}/metrics")
  public ImportMetrics projectSourceMetrics(@PathParam("key") int datasetKey, @PathParam("id") int id) {
    return sourceDao.sourceMetrics(datasetKey, id);
  }
}
