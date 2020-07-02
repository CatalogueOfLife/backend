package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.mapper.legacy.LNameMapper;
import life.catalogue.db.mapper.legacy.LVernacularMapper;
import life.catalogue.db.mapper.legacy.model.LError;
import life.catalogue.db.mapper.legacy.model.LName;
import life.catalogue.db.mapper.legacy.model.LResponse;
import life.catalogue.dw.jersey.filter.ApplyFormatFilter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@ApplyFormatFilter
@Produces({MediaType.TEXT_XML, MediaType.APPLICATION_JSON})
@Path("/dataset/{datasetKey}/legacy")
public class LegacyWebserviceResource {
  static int LIMIT_TERSE = 100;
  static int LIMIT_FULL = 25;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(LegacyWebserviceResource.class);
  private final SqlSessionFactory factory;
  private final String version;

  public LegacyWebserviceResource(SqlSessionFactory factory, WsServerConfig cfg) {
    this.factory = factory;
    version = cfg.versionString();
  }

  @GET
  public LResponse searchOrGet(@PathParam("datasetKey") int datasetKey,
                      @QueryParam("id") String id,
                      @QueryParam("name") String name,
                      @QueryParam("response") @DefaultValue("terse") String response,
                      @QueryParam("start") @DefaultValue("0") int start,
                      @Context SqlSession session) {
    boolean full = response.equalsIgnoreCase("full");
    LResponse resp;
    if (StringUtils.hasContent(id)) {
      resp = get(datasetKey, id, full, session);

    } else if (StringUtils.hasContent(name)) {
      resp = search(datasetKey, name, full, start, session);

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
  private LResponse search (int datasetKey, final String name, boolean full, int start, SqlSession session) {
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

    int cntSN = sMapper.count(datasetKey, prefix, q);
    int cntVN = vMapper.count(datasetKey, prefix, q);
    if (cntSN + cntVN < start) {
      return nameNotFound(name, start);
    }
    int limit = full ? LIMIT_FULL : LIMIT_TERSE;
    List<LName> results;
    if (cntSN - start > 0) {
      // scientific and maybe vernaculars
      results = sMapper.search(datasetKey, prefix, q, start, limit);
      int vLimit = limit - results.size();
      if (cntVN > 0 && vLimit > 0) {
        results.addAll(vMapper.search(datasetKey, prefix, q, start, vLimit));
      }
    } else {
      // only vernaculars
      results = vMapper.search(datasetKey, prefix, q, start, limit);
    }
    return new LResponse(name, cntSN + cntVN, start, results, version);
  }

  private LResponse get (int datasetKey, String id, boolean full, SqlSession session) {
    LNameMapper mapper = session.getMapper(LNameMapper.class);
    LName obj = mapper.get(datasetKey, id);
    if (obj == null) {
      return idNotFound(id);
    }
    LResponse resp = new LResponse(id, obj, version);
    return resp;
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
