package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.config.MatchingConfig;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.es.suggest.NameUsageSuggestionService;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.AbstractMatchingJob;
import life.catalogue.matching.MatchingUtils;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * OpenRefine Reconciliation Service API (spec 0.2) over the CoL name matcher.
 * Exposes a service manifest, batch reconciliation (driven by the name matcher), data extension
 * (taxon properties + classification), and entity/property suggest aids.
 *
 * Subclasses bind a concrete @Path and supply the {@link UsageMatcher} for a dataset, mirroring the
 * {@link life.catalogue.resources.matching.AbstractNameUsageMatchingResource} pattern.
 */
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class AbstractReconciliationResource {
  protected static final String DEFAULT_DATASET_KEY = "-99";
  private static final int SUGGEST_LIMIT = 25;

  protected final MatchingConfig cfg;
  protected final NameUsageSuggestionService suggestService;
  protected final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private final URI apiURI;
  private final URI clbURI;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  public AbstractReconciliationResource(MatchingConfig cfg, NameUsageSuggestionService suggestService,
                                        SqlSessionFactory factory, UsageMatcherFactory matcherFactory, URI apiURI, URI clbURI) {
    this.cfg = cfg;
    this.suggestService = suggestService;
    this.factory = factory;
    this.matcherFactory = matcherFactory;
    this.apiURI = apiURI;
    this.clbURI = clbURI;
  }

  /** Returns the matcher to reconcile against for the given dataset. Override to plug in a fixed matcher. */
  public UsageMatcher singleMatchMatcher(int datasetKey) {
    try {
      return matcherFactory.existingOrPostgres(datasetKey);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Resolves the path dataset key to the concrete key to reconcile against. By default this is the
   * key itself (the alias filter has already rewritten things like {@code 3LXR}). The default
   * endpoint overrides this to resolve the latest COL extended release at request time.
   */
  protected int resolveDatasetKey(int pathKey) {
    return pathKey;
  }

  protected String apiBase() {
    return apiURI == null ? "" : StringUtils.removeEnd(apiURI.toString(), "/");
  }

  /** Base URL of this service, used for the manifest's self and sub-service URLs. */
  protected String reconcileBaseUrl(int datasetKey) {
    return apiBase() + "/dataset/" + datasetKey + "/reconcile";
  }

  private String clbBase() {
    return clbURI == null ? "https://www.checklistbank.org" : StringUtils.removeEnd(clbURI.toString(), "/");
  }

  // ---- Manifest + reconcile ----

  @GET
  public Object root(@PathParam("key") @DefaultValue(DEFAULT_DATASET_KEY) int datasetKey,
                     @QueryParam("queries") String queries) {
    int key = resolveDatasetKey(datasetKey);
    if (queries == null) {
      return OpenRefineMapper.manifest(key, reconcileBaseUrl(key), clbBase());
    }
    return reconcile(key, queries);
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Map<String, OpenRefineModel.Result> reconcileForm(@PathParam("key") @DefaultValue(DEFAULT_DATASET_KEY) int datasetKey,
                                                           @FormParam("queries") String queries) {
    return reconcile(resolveDatasetKey(datasetKey), queries);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Map<String, OpenRefineModel.Result> reconcileJson(@PathParam("key") @DefaultValue(DEFAULT_DATASET_KEY) int datasetKey,
                                                           String body) {
    // newer clients post {"queries": {...}}; older ones post the bare queries object
    String queries = body;
    try {
      JsonNode node = ApiModule.MAPPER.readTree(body);
      if (node != null && node.has("queries")) {
        queries = node.get("queries").toString();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid JSON body: " + e.getMessage());
    }
    return reconcile(resolveDatasetKey(datasetKey), queries);
  }

  private Map<String, OpenRefineModel.Result> reconcile(int datasetKey, String queriesJson) {
    Map<String, OpenRefineModel.Query> queries;
    try {
      queries = OpenRefineMapper.parseQueries(queriesJson, ApiModule.MAPPER);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid queries payload: " + e.getMessage());
    }
    Map<String, OpenRefineModel.Result> out = new LinkedHashMap<>();
    try (var matcher = singleMatchMatcher(datasetKey)) {
      MatchingUtils utils = new MatchingUtils(matcher.getNameIndex());
      for (var e : queries.entrySet()) {
        out.put(e.getKey(), reconcileSingle(e.getValue(), matcher, utils));
      }
    }
    return out;
  }

  private OpenRefineModel.Result reconcileSingle(OpenRefineModel.Query q, UsageMatcher matcher, MatchingUtils utils) {
    var sn = toSimpleName(q);
    if (sn == null || StringUtils.isBlank(sn.getName())) {
      return new OpenRefineModel.Result();
    }
    IssueContainer issues = new IssueContainer.Simple();
    UsageMatch match = AbstractMatchingJob.interpretAndMatch(sn, sn.getClassification(), issues, true, interpreter, utils, matcher);
    return OpenRefineMapper.toResult(match);
  }

  /** Builds a query name from the OpenRefine query string and its property hints. */
  private SimpleNameClassified<SimpleNameCached> toSimpleName(OpenRefineModel.Query q) {
    if (q == null || StringUtils.isBlank(q.query)) {
      return null;
    }
    String authorship = null;
    Rank rank = null;
    NomCode code = null;
    List<SimpleNameCached> classification = new ArrayList<>();
    if (q.properties != null) {
      for (var p : q.properties) {
        String val = textValue(p.v);
        if (p.pid == null || val == null) continue;
        switch (p.pid) {
          case "authorship":
            authorship = val;
            break;
          case "code":
            code = SafeParser.parse(NomCodeParser.PARSER, val).orNull();
            break;
          case "rank":
            rank = SafeParser.parse(RankParser.PARSER, val).orNull();
            break;
          default:
            Rank r = SafeParser.parse(RankParser.PARSER, p.pid).orNull();
            if (r != null) {
              classification.add(SimpleNameClassified.snc(null, r, null, null, val, null));
            }
        }
      }
    }
    var sn = SimpleNameClassified.snc(null, rank, code, null, q.query, authorship);
    if (!classification.isEmpty()) {
      sn.setClassification(classification);
    }
    return sn;
  }

  private static String textValue(JsonNode v) {
    if (v == null || v.isNull()) return null;
    String s = v.isValueNode() ? v.asText() : v.toString();
    return StringUtils.trimToNull(s);
  }

  // ---- Data extension ----

  @POST
  @Path("extend")
  @Consumes(MediaType.APPLICATION_JSON)
  public OpenRefineModel.ExtendResponse extend(@PathParam("key") @DefaultValue(DEFAULT_DATASET_KEY) int datasetKey,
                                               OpenRefineModel.ExtendQuery query) {
    int datasetKeyResolved = resolveDatasetKey(datasetKey);
    List<String> ids = query.ids == null ? List.of() : query.ids;
    List<String> propertyIds = query.properties == null ? List.of()
      : query.properties.stream().map(p -> p.id).collect(Collectors.toList());

    Map<String, SimpleNameClassified<SimpleNameCached>> usages = new LinkedHashMap<>();
    if (factory != null && !ids.isEmpty()) {
      try (SqlSession session = factory.openSession()) {
        var num = session.getMapper(NameUsageMapper.class);
        var tm = session.getMapper(TaxonMapper.class);
        for (String id : ids) {
          DSID<String> key = DSID.of(datasetKeyResolved, id);
          SimpleNameCached u = num.getSimpleCached(key);
          if (u != null) {
            SimpleNameClassified<SimpleNameCached> snc = new SimpleNameClassified<>(u);
            List<SimpleName> cl = tm.classificationSimple(key);
            if (cl != null) {
              snc.setClassification(cl.stream().map(SimpleNameCached::new).collect(Collectors.toList()));
            }
            usages.put(id, snc);
          }
        }
      }
    }
    return OpenRefineMapper.buildExtendResponse(ids, propertyIds, usages);
  }

  @GET
  @Path("extend/propose")
  public OpenRefineModel.ProposeResponse proposeProperties() {
    return new OpenRefineModel.ProposeResponse(OpenRefineMapper.EXTENSION_PROPERTIES);
  }

  // ---- Suggest ----

  @GET
  @Path("suggest/entity")
  public OpenRefineModel.SuggestResponse suggestEntity(@PathParam("key") @DefaultValue(DEFAULT_DATASET_KEY) int datasetKey,
                                                       @QueryParam("prefix") String prefix) {
    if (StringUtils.isBlank(prefix)) {
      return new OpenRefineModel.SuggestResponse();
    }
    var req = new NameUsageSuggestRequest();
    req.setQ(prefix);
    req.setLimit(SUGGEST_LIMIT);
    req.setDatasetFilter(resolveDatasetKey(datasetKey));
    List<NameUsageSuggestion> suggestions = suggestService.suggest(req);
    return OpenRefineMapper.toSuggestResponse(suggestions);
  }

  @GET
  @Path("suggest/property")
  public OpenRefineModel.SuggestResponse suggestProperty(@QueryParam("prefix") String prefix) {
    var resp = new OpenRefineModel.SuggestResponse();
    String p = prefix == null ? "" : prefix.toLowerCase();
    for (var prop : OpenRefineMapper.EXTENSION_PROPERTIES) {
      if (p.isEmpty() || prop.id.toLowerCase().startsWith(p) || prop.name.toLowerCase().startsWith(p)) {
        var item = new OpenRefineModel.SuggestItem();
        item.id = prop.id;
        item.name = prop.name;
        resp.result.add(item);
      }
    }
    return resp;
  }
}
