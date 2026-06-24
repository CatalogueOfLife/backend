package life.catalogue.resources.parser.openrefine;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.resources.matching.openrefine.OpenRefineMapper;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

/**
 * Shared OpenRefine reconciliation HTTP plumbing for the dedicated parser services
 * (name, geotime, taxgroup, area). Subclasses bind a concrete {@code @Path} and implement the hooks.
 * Mirrors {@link life.catalogue.resources.matching.openrefine.AbstractReconciliationResource}.
 */
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractParserReconciliationResource {
  protected static final int SUGGEST_LIMIT = 25;
  protected final URI apiURI;
  protected final URI clbURI;

  protected AbstractParserReconciliationResource(URI apiURI, URI clbURI) {
    this.apiURI = apiURI;
    this.clbURI = clbURI;
  }

  // ---- hooks ----
  /** path segment after /parser/, e.g. "name" -> /parser/name/reconcile */
  protected abstract String typePath();
  protected abstract OpenRefineModel.Manifest manifest(String reconcileBaseUrl, String clbBase);
  protected abstract OpenRefineModel.Result reconcileSingle(OpenRefineModel.Query q, MultivaluedMap<String, String> params);
  protected abstract List<OpenRefineModel.ExtendProperty> extendProperties();
  protected abstract String proposeType();
  /** value for one id and one property; null -> empty cell */
  protected abstract String extendValue(String id, OpenRefineModel.ExtendProperty prop, MultivaluedMap<String, String> params);
  /** default: no suggest service */
  protected OpenRefineModel.SuggestResponse suggestEntity(String prefix) {
    return new OpenRefineModel.SuggestResponse();
  }

  protected String apiBase() {
    return apiURI == null ? "" : StringUtils.removeEnd(apiURI.toString(), "/");
  }
  protected String reconcileBaseUrl() {
    return apiBase() + "/parser/" + typePath() + "/reconcile";
  }
  protected String clbBase() {
    return clbURI == null ? "https://www.checklistbank.org" : StringUtils.removeEnd(clbURI.toString(), "/");
  }

  @GET
  public Object root(@QueryParam("queries") String queries, @Context UriInfo uriInfo) {
    if (queries == null) {
      return manifest(reconcileBaseUrl(), clbBase());
    }
    return reconcile(queries, uriInfo.getQueryParameters());
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Map<String, OpenRefineModel.Result> reconcileForm(@FormParam("queries") String queries, @Context UriInfo uriInfo) {
    return reconcile(queries, uriInfo.getQueryParameters());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Map<String, OpenRefineModel.Result> reconcileJson(String body, @Context UriInfo uriInfo) {
    String queries = body;
    try {
      JsonNode node = ApiModule.MAPPER.readTree(body);
      if (node != null && node.has("queries")) {
        queries = node.get("queries").toString();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid JSON body: " + e.getMessage());
    }
    return reconcile(queries, uriInfo.getQueryParameters());
  }

  private Map<String, OpenRefineModel.Result> reconcile(String queriesJson, MultivaluedMap<String, String> params) {
    Map<String, OpenRefineModel.Query> queries;
    try {
      queries = OpenRefineMapper.parseQueries(queriesJson, ApiModule.MAPPER);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid queries payload: " + e.getMessage());
    }
    Map<String, OpenRefineModel.Result> out = new LinkedHashMap<>();
    for (var e : queries.entrySet()) {
      var q = e.getValue();
      out.put(e.getKey(), q == null || StringUtils.isBlank(q.query)
        ? new OpenRefineModel.Result()
        : reconcileSingle(q, params));
    }
    return out;
  }

  @POST
  @Path("extend")
  @Consumes(MediaType.APPLICATION_JSON)
  public OpenRefineModel.ExtendResponse extend(OpenRefineModel.ExtendQuery query, @Context UriInfo uriInfo) {
    var params = uriInfo.getQueryParameters();
    List<String> ids = query == null || query.ids == null ? List.of() : query.ids;
    List<OpenRefineModel.ExtendProperty> props = query == null || query.properties == null ? List.of() : query.properties;
    var resp = new OpenRefineModel.ExtendResponse();
    for (var p : props) {
      resp.meta.add(new OpenRefineModel.ExtendProperty(p.id, propertyName(p.id)));
    }
    for (String id : ids) {
      var row = new LinkedHashMap<String, List<OpenRefineModel.Cell>>();
      for (var p : props) {
        String value = extendValue(id, p, params);
        row.put(p.id, value == null ? List.of() : List.of(new OpenRefineModel.Cell(value)));
      }
      resp.rows.put(id, row);
    }
    return resp;
  }

  @GET
  @Path("extend/propose")
  public OpenRefineModel.ProposeResponse propose() {
    var resp = new OpenRefineModel.ProposeResponse(extendProperties());
    resp.type = proposeType();
    return resp;
  }

  @GET
  @Path("suggest/entity")
  public OpenRefineModel.SuggestResponse suggest(@QueryParam("prefix") String prefix) {
    return suggestEntity(prefix);
  }

  private String propertyName(String pid) {
    return extendProperties().stream().filter(p -> p.id.equals(pid)).map(p -> p.name).findFirst().orElse(pid);
  }
}
