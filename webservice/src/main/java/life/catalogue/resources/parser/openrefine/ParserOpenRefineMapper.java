package life.catalogue.resources.parser.openrefine;

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
}
