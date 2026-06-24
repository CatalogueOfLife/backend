package life.catalogue.resources.parser.openrefine;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.parser.EnumParser;
import life.catalogue.parser.Parser;
import life.catalogue.parser.Parsers;
import life.catalogue.resources.matching.openrefine.OpenRefineMapper;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** OpenRefine reconciliation for the controlled-vocabulary parsers, one service per {type}. */
@Path("/parser/{type}/reconcile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VocabReconciliationResource {
  private static final int SUGGEST_LIMIT = 25;
  // handled by dedicated resources, never by the generic one
  private static final Set<String> RESERVED = Set.of("name", "geotime", "taxgroup", "area");
  private final URI apiURI;
  private final URI clbURI;

  public VocabReconciliationResource(URI apiURI, URI clbURI) {
    this.apiURI = apiURI;
    this.clbURI = clbURI;
  }

  private Parser<?> parserOr404(String type) {
    if (type != null && !RESERVED.contains(type.toLowerCase())) {
      Parser<?> p = Parsers.get(type);
      if (p != null) return p;
    }
    throw new NotFoundException("No reconciliation parser for " + type);
  }

  private String reconcileBaseUrl(String type) {
    return (apiURI == null ? "" : StringUtils.removeEnd(apiURI.toString(), "/")) + "/parser/" + type + "/reconcile";
  }

  private String clbBase() {
    return clbURI == null ? "https://www.checklistbank.org" : StringUtils.removeEnd(clbURI.toString(), "/");
  }

  @GET
  public Object root(@PathParam("type") String type, @QueryParam("queries") String queries) {
    Parser<?> parser = parserOr404(type);
    if (queries == null) {
      boolean enumBacked = parser instanceof EnumParser;
      return ParserOpenRefineMapper.vocabManifest(type.toLowerCase(), reconcileBaseUrl(type.toLowerCase()), clbBase(), enumBacked);
    }
    return reconcile(parser, type, queries);
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Map<String, OpenRefineModel.Result> reconcileForm(@PathParam("type") String type, @FormParam("queries") String queries) {
    return reconcile(parserOr404(type), type, queries);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Map<String, OpenRefineModel.Result> reconcileJson(@PathParam("type") String type, String body) {
    String queries = body;
    try {
      JsonNode node = ApiModule.MAPPER.readTree(body);
      if (node != null && node.has("queries")) {
        queries = node.get("queries").toString();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid JSON body: " + e.getMessage());
    }
    return reconcile(parserOr404(type), type, queries);
  }

  private Map<String, OpenRefineModel.Result> reconcile(Parser<?> parser, String type, String queriesJson) {
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
        : ParserOpenRefineMapper.vocabResult(parser, q.query));
    }
    return out;
  }

  @GET
  @Path("suggest/entity")
  public OpenRefineModel.SuggestResponse suggestEntity(@PathParam("type") String type, @QueryParam("prefix") String prefix) {
    Parser<?> parser = parserOr404(type);
    if (parser instanceof EnumParser) {
      return ParserOpenRefineMapper.enumSuggest(((EnumParser<?>) parser).getEnumClass(), prefix, SUGGEST_LIMIT);
    }
    return new OpenRefineModel.SuggestResponse();
  }
}
