package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

/** Builds a query SimpleName from an OpenRefine reconciliation query + its property hints. */
public class OpenRefineQueries {
  private OpenRefineQueries() {}

  public static SimpleNameClassified<SimpleNameCached> toSimpleName(OpenRefineModel.Query q) {
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
          case "authorship": authorship = val; break;
          case "code": code = SafeParser.parse(NomCodeParser.PARSER, val).orNull(); break;
          case "rank": rank = SafeParser.parse(RankParser.PARSER, val).orNull(); break;
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
}
