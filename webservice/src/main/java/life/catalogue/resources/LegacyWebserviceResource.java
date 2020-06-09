package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.legacy.mapper.LNameMapper;
import life.catalogue.db.legacy.mapper.LVernacularMapper;
import life.catalogue.db.legacy.model.LError;
import life.catalogue.db.legacy.model.LName;
import life.catalogue.db.legacy.model.LResponse;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Produces({MediaType.TEXT_XML, MediaType.APPLICATION_JSON})
@Path("/webservice")
public class LegacyWebserviceResource {
  static int LIMIT_TERSE = 500;
  static int LIMIT_FULL = 50;

  int latestReleaseKey;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(LegacyWebserviceResource.class);
  private final SqlSessionFactory factory;
  private final String version;

  public LegacyWebserviceResource(SqlSessionFactory factory, WsServerConfig cfg) {
    this.factory = factory;
    version = cfg.versionString();
    updateLatest();
  }

  @GET
  public LResponse searchOrGet(@QueryParam("id") String id,
                      @QueryParam("name") String name,
                      @QueryParam("response") @DefaultValue("terse") String response,
                      @QueryParam("start") @DefaultValue("0") int start,
                      @Context SqlSession session) {
    boolean full = response.equalsIgnoreCase("full");
    LResponse resp;
    if (StringUtils.hasContent(id)) {
      resp = get(id, full, session);

    } else if (StringUtils.hasContent(name)) {
      resp = search(name, full, start, session);

    } else {
      resp = new LError(null, null, "No name or ID given", version);
    }
    return resp;
  }

  /**
   * Searches on both scientific and vernacular names.
   * First list scientific hits, then vernaculars.
   * Matches are only exact or prefix if an asterisk is used.
   *
   * @param name
   * @param full
   * @param start
   * @param session
   * @return
   */
  private LResponse search (final String name, boolean full, int start, SqlSession session) {
    LNameMapper sMapper = session.getMapper(LNameMapper.class);
    LVernacularMapper vMapper = session.getMapper(LVernacularMapper.class);
    boolean prefix = false;
    String q = name;
    if (name.endsWith("*")) {
      q = org.apache.commons.lang3.StringUtils.chop(name);
      prefix = true;
    }
    if (q.length() < 3) {
      return invalidName(name);
    }

    int cntSN = sMapper.count(latestReleaseKey, prefix, q);
    int cntVN = vMapper.count(latestReleaseKey, prefix, q);
    if (cntSN + cntVN < start) {
      return nameNotFound(name, start);
    }
    int limit = full ? LIMIT_FULL : LIMIT_TERSE;
    List<LName> results;
    if (cntSN - start > 0) {
      // scientific and maybe vernaculars
      results = sMapper.search(latestReleaseKey, prefix, q, start, limit);
      int vLimit = limit - results.size();
      if (cntVN > 0 && vLimit > 0) {
        results.addAll(vMapper.search(latestReleaseKey, prefix, q, start, vLimit));
      }
    } else {
      // only vernaculars
      results = vMapper.search(latestReleaseKey, prefix, q, start, limit);
    }
    return new LResponse(name, cntSN + cntVN, start, results, version);
  }

  private LResponse get (String id, boolean full, SqlSession session) {
    LNameMapper mapper = session.getMapper(LNameMapper.class);
    LName obj = mapper.get(latestReleaseKey, id);
    if (obj == null) {
      return idNotFound(id);
    }
    LResponse resp = new LResponse(id, obj, version);
    return resp;
  }

  @POST
  @Path("updateLatest")
  @RolesAllowed({Roles.ADMIN})
  public Integer updateLatestWithContent(int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(datasetKey);
      if (d == null) {
        throw NotFoundException.notFound(Dataset.class, datasetKey);
      } else if (d.hasDeletedDate()) {
        throw new IllegalArgumentException("Dataset " + datasetKey + " is deleted");
      }
      if (!session.getMapper(DatasetPartitionMapper.class).exists(datasetKey)) {
        throw new IllegalArgumentException("Dataset " + datasetKey + " has no data");
      }
      LOG.info("Use provided datasetKey {} as new CoL release instead of {}", datasetKey, latestReleaseKey);
      latestReleaseKey = datasetKey;
      return latestReleaseKey;
    }
  }

  @PUT
  @Path("updateLatest")
  @RolesAllowed({Roles.ADMIN})
  public Integer updateLatest() {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      int datasetKey = ObjectUtils.coalesce(dm.latestRelease(Datasets.DRAFT_COL), Datasets.DRAFT_COL);
      LOG.info("Use latest CoL release {} instead of {}", datasetKey, latestReleaseKey);
      latestReleaseKey = datasetKey;
      return latestReleaseKey;
    }
  }

  LError idNotFound(String id) {
    return new LError(id, null, "ID not found", version);
  }

  LError nameNotFound(String name, int start) {
    return new LError(null, name, start, "No names found", version);
  }

  /**
   * Dont use this - it validates legacy hash ids only
   */
  @Deprecated
  LError invalidID(String id) {
    return new LError(id, null, "Invalid ID given. The ID must be a positive integer or a 32-character string", version);
  }

  LError invalidName(String name) {
    return new LError(null, name, "Invalid name given. The name given must consist of at least 3 characters", version);
  }

}
