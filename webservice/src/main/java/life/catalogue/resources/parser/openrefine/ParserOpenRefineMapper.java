package life.catalogue.resources.parser.openrefine;

import life.catalogue.api.vocab.GeoTime;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.parser.Parser;
import life.catalogue.parser.SafeParser;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import org.apache.commons.lang3.StringUtils;

/** Pure mapping between the CoL parsers and the OpenRefine reconciliation protocol. */
public class ParserOpenRefineMapper {
  private ParserOpenRefineMapper() {}

  public static OpenRefineModel.Type vocabType(String type) {
    return new OpenRefineModel.Type(type, type);
  }

  /** One auto-matched candidate for a parsable value, else an empty result. */
  public static OpenRefineModel.Result vocabResult(Parser<?> parser, String type, String value) {
    var result = new OpenRefineModel.Result();
    Object parsed = SafeParser.parse(parser, value).orNull();
    if (parsed != null) {
      var c = new OpenRefineModel.Candidate();
      c.id = parsed instanceof Enum ? ((Enum<?>) parsed).name() : parsed.toString();
      c.name = parsed.toString();
      c.score = 100;
      c.match = true;
      c.type.add(vocabType(type.toLowerCase()));
      result.result.add(c);
    }
    return result;
  }

  // EnumParser uses a raw Enum bound, so we cannot tighten this to Enum<?> here
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static OpenRefineModel.SuggestResponse enumSuggest(Class<? extends Enum> enumClass, String prefix, int limit) {
    var resp = new OpenRefineModel.SuggestResponse();
    String p = prefix == null ? "" : prefix.toLowerCase();
    for (Enum<?> e : enumClass.getEnumConstants()) {
      if (resp.result.size() >= limit) break;
      if (p.isEmpty() || e.name().toLowerCase().startsWith(p) || e.toString().toLowerCase().startsWith(p)) {
        var item = new OpenRefineModel.SuggestItem();
        item.id = e.name();
        item.name = e.toString();
        resp.result.add(item);
      }
    }
    return resp;
  }

  public static OpenRefineModel.Manifest vocabManifest(String type, String apiReconcileUrl, String clbBaseUrl, boolean enumBacked) {
    var m = new OpenRefineModel.Manifest();
    m.name = "Catalogue of Life — " + type + " parser";
    String space = StringUtils.removeEnd(clbBaseUrl, "/") + "/vocabulary/" + type;
    m.identifierSpace = space;
    m.schemaSpace = space;
    m.defaultTypes.add(vocabType(type));
    if (enumBacked) {
      m.suggest = new OpenRefineModel.SuggestServices();
      m.suggest.entity = new OpenRefineModel.SuggestService(apiReconcileUrl, "/suggest/entity");
    }
    return m;
  }

  public static final OpenRefineModel.Type NAME_TYPE = new OpenRefineModel.Type("Name", "Name");

  public static final java.util.List<OpenRefineModel.ExtendProperty> NAME_PROPERTIES = java.util.List.of(
    new OpenRefineModel.ExtendProperty("label", "label"),
    new OpenRefineModel.ExtendProperty("labelHtml", "label (HTML)"),
    new OpenRefineModel.ExtendProperty("scientificName", "scientific name"),
    new OpenRefineModel.ExtendProperty("authorship", "authorship"),
    new OpenRefineModel.ExtendProperty("rank", "rank"),
    new OpenRefineModel.ExtendProperty("code", "nomenclatural code"),
    new OpenRefineModel.ExtendProperty("type", "name type"),
    new OpenRefineModel.ExtendProperty("uninomial", "uninomial"),
    new OpenRefineModel.ExtendProperty("genus", "genus"),
    new OpenRefineModel.ExtendProperty("infragenericEpithet", "infrageneric epithet"),
    new OpenRefineModel.ExtendProperty("specificEpithet", "specific epithet"),
    new OpenRefineModel.ExtendProperty("infraspecificEpithet", "infraspecific epithet"),
    new OpenRefineModel.ExtendProperty("cultivarEpithet", "cultivar epithet"),
    new OpenRefineModel.ExtendProperty("combinationAuthorship", "combination authorship"),
    new OpenRefineModel.ExtendProperty("combinationYear", "combination year"),
    new OpenRefineModel.ExtendProperty("basionymAuthorship", "basionym authorship"),
    new OpenRefineModel.ExtendProperty("basionymYear", "basionym year"),
    new OpenRefineModel.ExtendProperty("nomenclaturalNote", "nomenclatural note"),
    new OpenRefineModel.ExtendProperty("taxonomicNote", "taxonomic note"),
    new OpenRefineModel.ExtendProperty("extinct", "extinct"),
    new OpenRefineModel.ExtendProperty("parsed", "parsed")
  );

  public static OpenRefineModel.Manifest nameManifest(String apiReconcileUrl, String clbBaseUrl) {
    var m = new OpenRefineModel.Manifest();
    m.name = "Catalogue of Life — name parser";
    String space = org.apache.commons.lang3.StringUtils.removeEnd(clbBaseUrl, "/") + "/tools/name-parser";
    m.identifierSpace = space;
    m.schemaSpace = space;
    m.defaultTypes.add(NAME_TYPE);
    var extend = new OpenRefineModel.ExtendService(
      new OpenRefineModel.PropertySettings(apiReconcileUrl, "/extend/propose"));
    extend.property_settings = java.util.List.of(codeSetting(), rankSetting());
    m.extend = extend;
    return m;
  }

  private static OpenRefineModel.PropertySetting codeSetting() {
    var s = new OpenRefineModel.PropertySetting();
    s.name = "code"; s.label = "Nomenclatural code"; s.type = "select"; s.default_ = "";
    s.choices = new java.util.ArrayList<>();
    s.choices.add(new OpenRefineModel.SettingChoice("", "auto-detect"));
    for (org.gbif.nameparser.api.NomCode c : org.gbif.nameparser.api.NomCode.values()) {
      s.choices.add(new OpenRefineModel.SettingChoice(c.name(), c.getAcronym() == null ? c.name() : c.getAcronym()));
    }
    return s;
  }

  private static OpenRefineModel.PropertySetting rankSetting() {
    var s = new OpenRefineModel.PropertySetting();
    s.name = "rank"; s.label = "Rank"; s.type = "select"; s.default_ = "";
    s.choices = new java.util.ArrayList<>();
    s.choices.add(new OpenRefineModel.SettingChoice("", "auto-detect"));
    for (org.gbif.nameparser.api.Rank r : org.gbif.nameparser.api.Rank.values()) {
      s.choices.add(new OpenRefineModel.SettingChoice(r.name(), r.name().toLowerCase()));
    }
    return s;
  }

  public static String nameValue(life.catalogue.api.model.Name n, life.catalogue.api.model.ParsedNameUsage pnu, String pid) {
    if (n == null || pid == null) return null;
    switch (pid) {
      case "label": return n.getLabel();
      case "labelHtml": return n.getLabelHtml();
      case "scientificName": return n.getScientificName();
      case "authorship": return n.getAuthorship();
      case "rank": return n.getRank() == null ? null : n.getRank().name().toLowerCase();
      case "code": return n.getCode() == null ? null : n.getCode().name();
      case "type": return n.getType() == null ? null : n.getType().name();
      case "uninomial": return n.getUninomial();
      case "genus": return n.getGenus();
      case "infragenericEpithet": return n.getInfragenericEpithet();
      case "specificEpithet": return n.getSpecificEpithet();
      case "infraspecificEpithet": return n.getInfraspecificEpithet();
      case "cultivarEpithet": return n.getCultivarEpithet();
      case "combinationAuthorship": return authorship(n.getCombinationAuthorship());
      case "combinationYear": return year(n.getCombinationAuthorship());
      case "basionymAuthorship": return authorship(n.getBasionymAuthorship());
      case "basionymYear": return year(n.getBasionymAuthorship());
      case "nomenclaturalNote": return n.getNomenclaturalNote();
      case "taxonomicNote": return pnu == null ? null : pnu.getTaxonomicNote();
      case "extinct": return pnu == null ? null : String.valueOf(pnu.isExtinct());
      case "parsed": return String.valueOf(n.getType() != null && n.getType().isParsable());
      default: return null;
    }
  }

  private static String authorship(org.gbif.nameparser.api.Authorship a) {
    return a == null || a.isEmpty() ? null : a.toString();
  }

  private static String year(org.gbif.nameparser.api.Authorship a) {
    return a == null ? null : a.getYear();
  }

  public static final OpenRefineModel.Type GEOTIME_TYPE = new OpenRefineModel.Type("GeoTime", "GeoTime");

  public static final java.util.List<OpenRefineModel.ExtendProperty> GEOTIME_PROPERTIES = java.util.List.of(
    new OpenRefineModel.ExtendProperty("name", "name"),
    new OpenRefineModel.ExtendProperty("type", "type"),
    new OpenRefineModel.ExtendProperty("start", "start (Ma)"),
    new OpenRefineModel.ExtendProperty("end", "end (Ma)")
  );

  public static OpenRefineModel.Manifest geoTimeManifest(String apiReconcileUrl, String clbBaseUrl) {
    var m = new OpenRefineModel.Manifest();
    m.name = "Catalogue of Life — geochronology (GeoTime)";
    String space = org.apache.commons.lang3.StringUtils.removeEnd(clbBaseUrl, "/") + "/vocabulary/geotime";
    m.identifierSpace = space; m.schemaSpace = space;
    m.defaultTypes.add(GEOTIME_TYPE);
    m.suggest = new OpenRefineModel.SuggestServices();
    m.suggest.entity = new OpenRefineModel.SuggestService(apiReconcileUrl, "/suggest/entity");
    m.extend = new OpenRefineModel.ExtendService(new OpenRefineModel.PropertySettings(apiReconcileUrl, "/extend/propose"));
    return m;
  }

  public static OpenRefineModel.SuggestResponse geoTimeSuggest(String prefix, int limit) {
    var resp = new OpenRefineModel.SuggestResponse();
    String p = prefix == null ? "" : prefix.toLowerCase();
    for (GeoTime gt : GeoTime.TIMES.values()) {
      if (resp.result.size() >= limit) break;
      if (p.isEmpty() || gt.getName().toLowerCase().startsWith(p)) {
        var item = new OpenRefineModel.SuggestItem();
        item.id = gt.getName(); item.name = gt.getName();
        item.description = gt.getType() == null ? null : gt.getType().name();
        resp.result.add(item);
      }
    }
    return resp;
  }

  public static String geoTimeValue(GeoTime gt, String pid) {
    if (gt == null || pid == null) return null;
    switch (pid) {
      case "name": return gt.getName();
      case "type": return gt.getType() == null ? null : gt.getType().name();
      case "start": return gt.getStart() == null ? null : String.valueOf(gt.getStart());
      case "end": return gt.getEnd() == null ? null : String.valueOf(gt.getEnd());
      default: return null;
    }
  }

  public static final OpenRefineModel.Type TAXGROUP_TYPE = new OpenRefineModel.Type("TaxGroup", "TaxGroup");

  public static final java.util.List<OpenRefineModel.ExtendProperty> TAXGROUP_PROPERTIES = java.util.List.of(
    new OpenRefineModel.ExtendProperty("parent", "parent group"),
    new OpenRefineModel.ExtendProperty("codes", "nomenclatural codes"),
    new OpenRefineModel.ExtendProperty("description", "description"),
    new OpenRefineModel.ExtendProperty("icon", "icon URL")
  );

  public static OpenRefineModel.Manifest taxGroupManifest(String apiReconcileUrl, String clbBaseUrl) {
    var m = new OpenRefineModel.Manifest();
    m.name = "Catalogue of Life — taxonomic group";
    String space = org.apache.commons.lang3.StringUtils.removeEnd(clbBaseUrl, "/") + "/vocabulary/taxgroup";
    m.identifierSpace = space; m.schemaSpace = space;
    m.defaultTypes.add(TAXGROUP_TYPE);
    m.suggest = new OpenRefineModel.SuggestServices();
    m.suggest.entity = new OpenRefineModel.SuggestService(apiReconcileUrl, "/suggest/entity");
    m.extend = new OpenRefineModel.ExtendService(new OpenRefineModel.PropertySettings(apiReconcileUrl, "/extend/propose"));
    return m;
  }

  public static OpenRefineModel.SuggestResponse taxGroupSuggest(String prefix, int limit) {
    return enumSuggest(TaxGroup.class, prefix, limit);
  }

  public static String taxGroupValue(TaxGroup g, String pid) {
    if (g == null || pid == null) return null;
    switch (pid) {
      case "parent": return g.getPrimaryParent() == null ? null : g.getPrimaryParent().name();
      case "codes": return g.getCodes() == null || g.getCodes().isEmpty() ? null :
        g.getCodes().stream().map(Enum::name).collect(java.util.stream.Collectors.joining(";"));
      case "description": return g.getDescription();
      case "icon": return g.getIconSVG() == null ? null : g.getIconSVG().toString();
      default: return null;
    }
  }
}
