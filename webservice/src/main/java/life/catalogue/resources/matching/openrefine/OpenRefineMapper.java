package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.matching.UsageMatch;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gbif.nameparser.api.Rank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pure mapping between the CoL matching engine and the OpenRefine reconciliation protocol.
 * Kept free of HTTP and persistence concerns so it can be unit tested in isolation.
 */
public class OpenRefineMapper {
  /** The single reconciliation type CoL exposes. */
  public static final OpenRefineModel.Type TAXON_TYPE = new OpenRefineModel.Type("Taxon", "Taxon");

  private OpenRefineMapper() {}

  /** Score 0-100 derived from a names-index/usage match type. */
  public static double score(MatchType type) {
    if (type == null) return 0;
    switch (type) {
      case EXACT: return 100;
      case VARIANT: return 98;
      case CANONICAL: return 90;
      case AMBIGUOUS: return 75;
      case HIGHERRANK: return 50;
      default: return 0; // NONE, UNSUPPORTED
    }
  }

  /** Maps a usage match (primary + alternatives) to an OpenRefine result for a single query. */
  public static OpenRefineModel.Result toResult(UsageMatch match) {
    var result = new OpenRefineModel.Result();
    if (match != null && match.isMatch()) {
      // OpenRefine auto-matches a cell when a single candidate has match=true.
      // Only do that for unambiguous exact hits.
      boolean autoMatch = match.type == MatchType.EXACT;
      result.result.add(toCandidate(match.usage, match.type, autoMatch));
    }
    if (match != null && match.alternatives != null) {
      for (var alt : match.alternatives) {
        result.result.add(toCandidate(alt, alt.getNamesIndexMatchType(), false));
      }
    }
    return result;
  }

  static OpenRefineModel.Candidate toCandidate(SimpleNameClassified<SimpleNameCached> u, MatchType type, boolean match) {
    var c = new OpenRefineModel.Candidate();
    c.id = u.getId();
    c.name = u.getLabel();
    c.score = score(type);
    c.match = match;
    c.type.add(TAXON_TYPE);
    return c;
  }

  /** Builds the service manifest for a dataset-scoped reconciliation endpoint. */
  public static OpenRefineModel.Manifest manifest(int datasetKey, String apiReconcileUrl, String clbBaseUrl) {
    var m = new OpenRefineModel.Manifest();
    m.name = "Catalogue of Life — dataset " + datasetKey;
    String taxonSpace = clbBaseUrl + "/dataset/" + datasetKey + "/taxon/";
    m.identifierSpace = taxonSpace;
    m.schemaSpace = taxonSpace;
    m.defaultTypes.add(TAXON_TYPE);
    m.view = new OpenRefineModel.View(clbBaseUrl + "/dataset/" + datasetKey + "/taxon/{{id}}");
    m.suggest = new OpenRefineModel.SuggestServices();
    m.suggest.entity = new OpenRefineModel.SuggestService(apiReconcileUrl, "/suggest/entity");
    m.suggest.property = new OpenRefineModel.SuggestService(apiReconcileUrl, "/suggest/property");
    m.extend = new OpenRefineModel.ExtendService(
      new OpenRefineModel.PropertySettings(apiReconcileUrl, "/extend/propose")
    );
    return m;
  }

  /** The fixed catalogue of properties the data extension service can return for a matched taxon. */
  public static final List<OpenRefineModel.ExtendProperty> EXTENSION_PROPERTIES = List.of(
    new OpenRefineModel.ExtendProperty("scientificName", "scientific name"),
    new OpenRefineModel.ExtendProperty("authorship", "authorship"),
    new OpenRefineModel.ExtendProperty("rank", "rank"),
    new OpenRefineModel.ExtendProperty("status", "status"),
    new OpenRefineModel.ExtendProperty("nidx", "names index id"),
    new OpenRefineModel.ExtendProperty("kingdom", "kingdom"),
    new OpenRefineModel.ExtendProperty("phylum", "phylum"),
    new OpenRefineModel.ExtendProperty("class", "class"),
    new OpenRefineModel.ExtendProperty("order", "order"),
    new OpenRefineModel.ExtendProperty("family", "family"),
    new OpenRefineModel.ExtendProperty("genus", "genus")
  );

  /**
   * Resolves a single data extension property for a matched usage. Returns null when the property
   * is unknown or has no value for this usage.
   */
  public static String extendValue(SimpleNameClassified<SimpleNameCached> u, String propertyId) {
    if (u == null || propertyId == null) return null;
    switch (propertyId) {
      case "scientificName": return u.getName();
      case "authorship": return u.getAuthorship();
      case "rank": return u.getRank() == null ? null : u.getRank().name().toLowerCase();
      case "status": return u.getStatus() == null ? null : u.getStatus().name().toLowerCase();
      case "nidx": return u.getNamesIndexId() == null ? null : String.valueOf(u.getNamesIndexId());
      default:
        Rank rank = parseRank(propertyId);
        if (rank != null && u.getClassification() != null) {
          var t = u.getByRank(rank);
          return t == null ? null : t.getName();
        }
        return null;
    }
  }

  private static Rank parseRank(String propertyId) {
    try {
      return Rank.valueOf(propertyId.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Builds the data extension response for the given ids/properties from already-loaded usages. */
  public static OpenRefineModel.ExtendResponse buildExtendResponse(List<String> ids, List<String> propertyIds,
                                                                   Map<String, SimpleNameClassified<SimpleNameCached>> usages) {
    var resp = new OpenRefineModel.ExtendResponse();
    for (String pid : propertyIds) {
      resp.meta.add(new OpenRefineModel.ExtendProperty(pid, propertyName(pid)));
    }
    for (String id : ids) {
      var usage = usages.get(id);
      var row = new LinkedHashMap<String, List<OpenRefineModel.Cell>>();
      for (String pid : propertyIds) {
        String value = usage == null ? null : extendValue(usage, pid);
        row.put(pid, value == null ? List.of() : List.of(new OpenRefineModel.Cell(value)));
      }
      resp.rows.put(id, row);
    }
    return resp;
  }

  private static String propertyName(String pid) {
    return EXTENSION_PROPERTIES.stream()
      .filter(p -> p.id.equals(pid))
      .map(p -> p.name)
      .findFirst()
      .orElse(pid);
  }

  /** Maps name usage suggestions to an OpenRefine suggest/entity response. */
  public static OpenRefineModel.SuggestResponse toSuggestResponse(List<life.catalogue.api.search.NameUsageSuggestion> suggestions) {
    var resp = new OpenRefineModel.SuggestResponse();
    if (suggestions != null) {
      for (var s : suggestions) {
        var item = new OpenRefineModel.SuggestItem();
        item.id = s.getUsageId();
        item.name = s.getMatch();
        item.description = s.getSuggestion();
        item.type.add(TAXON_TYPE);
        resp.result.add(item);
      }
    }
    return resp;
  }

  /**
   * Parses the OpenRefine {@code queries} payload, a JSON object keyed by query id, into a map.
   */
  public static Map<String, OpenRefineModel.Query> parseQueries(String json, ObjectMapper om) throws IOException {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    return om.readValue(json, new TypeReference<LinkedHashMap<String, OpenRefineModel.Query>>() {});
  }
}
