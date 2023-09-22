package life.catalogue.resources.legacy;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.common.text.StringUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.legacy.LNameMapper;
import life.catalogue.db.mapper.legacy.LVernacularMapper;
import life.catalogue.db.mapper.legacy.model.LError;
import life.catalogue.db.mapper.legacy.model.LName;
import life.catalogue.db.mapper.legacy.model.LResponse;
import life.catalogue.dw.jersey.filter.LegacyAPI;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.net.URI;
import java.util.List;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import io.swagger.v3.oas.annotations.Hidden;

import static life.catalogue.api.util.ObjectUtils.coalesce;

/**
 * Old PHP API migrated to the new postgres db and java code.
 * http://webservice.catalogueoflife.org/col/webservice
 */
@Hidden
@LegacyAPI
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Path("/dataset/{key}/legacy")
public class LegacyWebserviceResource {
  static int DEFAULT_LIMIT_TERSE = 100;
  static int DEFAULT_LIMIT_FULL = 10;
  static int MAX_LIMIT_TERSE = 1000;
  static int MAX_LIMIT_FULL = 100;


  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(LegacyWebserviceResource.class);
  private final String version;
  private final IdMap idMap;
  private final URI portalURI;
  private final Timer searchTimer;
  private final Timer getTimer;
  private final SqlSessionFactory factory;

  public LegacyWebserviceResource(WsServerConfig cfg, IdMap idMap, MetricRegistry registry, SqlSessionFactory factory) {
    version = cfg.versionString();
    portalURI = cfg.portalURI;
    this.idMap = idMap;
    searchTimer = registry.timer("life.catalogue.resources.legacy.search");
    getTimer = registry.timer("life.catalogue.resources.legacy.get");
    this.factory = factory;
  }

  static int calcLimit(boolean full, Integer limitRequested){
    return Math.max(0, Math.min(
      coalesce(limitRequested, full ? DEFAULT_LIMIT_FULL : DEFAULT_LIMIT_TERSE),
      full ? MAX_LIMIT_FULL : MAX_LIMIT_TERSE
    ));
  }

  @GET
  public LResponse searchOrGet(@PathParam("key") int datasetKey,
                      @QueryParam("id") String id,
                      @QueryParam("name") String name,
                      @QueryParam("response") @DefaultValue("terse") String response,
                      @QueryParam("start") @DefaultValue("0") @Min(0) int start,
                      @QueryParam("limit") @Max(1000) Integer limit) {
    try {
      boolean full = response.equalsIgnoreCase("full");
      LResponse resp;
      if (StringUtils.hasContent(id)) {
        resp = get(datasetKey, id, full);

      } else if (StringUtils.hasContent(name)) {
        resp = search(datasetKey, name, full, start, limit);

      } else {
        resp = new LError(null, null, "No name or ID given", version);
      }
      return resp;

    } catch (Exception e) {
      String key = ShortUUID.build().toString();
      LOG.error("Legacy API error (ID {})", key, e);
      return new LError(id, name, "Application error (ID "+key+")", version);
    }
  }

  @GET
  @Path("{id}")
  @VaryAccept
  @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_XML})
  public Response redirectPortal(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return redirect(datasetKey, id, true);
  }

  @GET
  @Path("{id}")
  @VaryAccept
  @Produces(MediaType.APPLICATION_JSON)
  public Response redirectAPI(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return redirect(datasetKey, id, false);
  }

  private Response redirect(int datasetKey, String id, boolean portal) {
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.sourceKey != null && info.sourceKey == Datasets.COL && idMap.contains(id)) {
      String newID = idMap.lookup(id);
      URI target = portal ?
        portalURI.resolve("/data/taxon/" + newID) :
        URI.create("/dataset/"+datasetKey+"/nameusage/" + newID);
      return Response.status(Response.Status.FOUND).location(target).build();
    }
    throw NotFoundException.notFound("COL Legacy ID", id);
  }

  /**
   * Searches on both scientific and vernacular names.
   * First list scientific hits, then vernaculars.
   * Matches are only exact or prefix if an asterisk is used.
   */
  private LResponse search (int datasetKey, final String name, boolean full, int start, Integer limitRequested) {
    final Timer.Context ctxt = searchTimer.time();
    try (SqlSession session = factory.openSession()){
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
      int limit = calcLimit(full, limitRequested);
      List<LName> results;
      if (cntSN - start > 0) {
        // scientific and maybe vernaculars
        results = full ?
          sMapper.searchFull(datasetKey, prefix, q, start, limit) :
          sMapper.search(datasetKey, prefix, q, start, limit);
        int vLimit = limit - results.size();
        if (cntVN > 0 && vLimit > 0) {
          results.addAll(vMapper.search(datasetKey, prefix, q, start, vLimit));
        }
      } else {
        // only vernaculars
        results = vMapper.search(datasetKey, prefix, q, start, limit);
      }
      return new LResponse(name, cntSN + cntVN, start, results, version);

    } finally {
      ctxt.stop();
    }
  }

  private LResponse get (int datasetKey, String id, boolean full) {
    final Timer.Context ctxt = getTimer.time();
    try (SqlSession session = factory.openSession()){
      LNameMapper mapper = session.getMapper(LNameMapper.class);
      LName obj = full ?
        mapper.getFull(datasetKey, id) :
        mapper.get(datasetKey, id);
      if (obj == null) {
        return idNotFound(id);
      }
      LResponse resp = new LResponse(id, obj, version);
      return resp;
    } finally {
      ctxt.stop();
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
