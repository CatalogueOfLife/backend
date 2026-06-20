package life.catalogue.resources.matching.openrefine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Data transfer objects for the OpenRefine Reconciliation Service API (spec version 0.2).
 * See https://reconciliation-api.github.io/specs/0.2/
 *
 * These are simple, Jackson-serializable holders. The mapping from the CoL matching/parsing
 * engines onto these structures lives in {@link OpenRefineMapper}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenRefineModel {

  /** A reconciliation type, e.g. the single "Taxon" type CoL exposes. */
  public static class Type {
    public String id;
    public String name;

    public Type() {}

    public Type(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  /** A single reconciliation candidate returned for a query. */
  public static class Candidate {
    public String id;
    public String name;
    public double score;
    public boolean match;
    public List<Type> type = new ArrayList<>();
  }

  /** The result wrapper for a single query: {@code { "result": [ ... ] }}. */
  public static class Result {
    // the reconciliation spec requires "result" to always be present, even as an empty array,
    // otherwise OpenRefine reports "JSON response without result field". The global mapper uses
    // NON_EMPTY inclusion, so force ALWAYS here.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public List<Candidate> result = new ArrayList<>();
  }

  /** An incoming reconciliation query as sent by OpenRefine. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Query {
    public String query;
    public String type;
    public Integer limit;
    public List<QueryProperty> properties;
  }

  /** A property hint attached to an incoming query, e.g. {@code {"pid":"rank","v":"species"}}. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class QueryProperty {
    public String pid;
    public JsonNode v;
  }

  /** The service manifest returned on a GET to the reconciliation endpoint root. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Manifest {
    public List<String> versions = List.of("0.1", "0.2");
    public String name;
    public String identifierSpace;
    public String schemaSpace;
    public List<Type> defaultTypes = new ArrayList<>();
    public View view;
    public SuggestServices suggest;
    public ExtendService extend;
  }

  public static class View {
    public String url;

    public View() {}

    public View(String url) {
      this.url = url;
    }
  }

  public static class SuggestServices {
    public SuggestService entity;
    public SuggestService property;
  }

  public static class SuggestService {
    public String service_url;
    public String service_path;

    public SuggestService() {}

    public SuggestService(String service_url, String service_path) {
      this.service_url = service_url;
      this.service_path = service_path;
    }
  }

  public static class ExtendService {
    public PropertySettings propose_properties;

    public ExtendService() {}

    public ExtendService(PropertySettings propose_properties) {
      this.propose_properties = propose_properties;
    }
  }

  public static class PropertySettings {
    public String service_url;
    public String service_path;

    public PropertySettings() {}

    public PropertySettings(String service_url, String service_path) {
      this.service_url = service_url;
      this.service_path = service_path;
    }
  }

  // ---- Data extension ----

  /** Incoming data extension request: {@code { "ids": [...], "properties": [ {"id": ...} ] }}. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ExtendQuery {
    public List<String> ids = new ArrayList<>();
    public List<ExtendProperty> properties = new ArrayList<>();
  }

  public static class ExtendProperty {
    public String id;
    public String name;

    public ExtendProperty() {}

    public ExtendProperty(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  /** A single value cell in a data extension response. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Cell {
    public String str;

    public Cell() {}

    public Cell(String str) {
      this.str = str;
    }
  }

  /** Data extension response: column metadata plus per-id, per-property value cells. */
  public static class ExtendResponse {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public List<ExtendProperty> meta = new ArrayList<>();
    // id -> (propertyId -> cells)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Map<String, Map<String, List<Cell>>> rows = new LinkedHashMap<>();
  }

  // ---- Suggest ----

  public static class SuggestResponse {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public List<SuggestItem> result = new ArrayList<>();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class SuggestItem {
    public String id;
    public String name;
    public String description;
    public List<Type> type = new ArrayList<>();
  }

  /** Response of the extend/propose service listing the properties offered for data extension. */
  public static class ProposeResponse {
    public String type = "Taxon";
    public List<ExtendProperty> properties;

    public ProposeResponse() {}

    public ProposeResponse(List<ExtendProperty> properties) {
      this.properties = properties;
    }
  }
}
