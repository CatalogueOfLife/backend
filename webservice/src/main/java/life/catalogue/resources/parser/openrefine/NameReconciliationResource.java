package life.catalogue.resources.parser.openrefine;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.parser.NameParser;
import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.Parser;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MultivaluedMap;

@Path("/parser/name/reconcile")
public class NameReconciliationResource extends AbstractParserReconciliationResource {

  public NameReconciliationResource(URI apiURI, URI clbURI) {
    super(apiURI, clbURI);
  }

  @Override
  protected String typePath() {
    return "name";
  }

  @Override
  protected OpenRefineModel.Manifest manifest(String reconcileBaseUrl, String clbBase) {
    return ParserOpenRefineMapper.nameManifest(reconcileBaseUrl, clbBase);
  }

  @Override
  protected OpenRefineModel.Result reconcileSingle(OpenRefineModel.Query q, MultivaluedMap<String, String> params) {
    var result = new OpenRefineModel.Result();
    var pnu = parse(q.query, paramCode(params), paramRank(params));
    if (pnu != null && pnu.getName().getType() != null && pnu.getName().getType().isParsable()) {
      var c = new OpenRefineModel.Candidate();
      c.id = q.query; // raw input -> re-parseable at extend
      c.name = pnu.getName().getLabel();
      c.score = 100;
      c.match = true;
      c.type.add(ParserOpenRefineMapper.NAME_TYPE);
      result.result.add(c);
    }
    return result;
  }

  @Override
  protected List<OpenRefineModel.ExtendProperty> extendProperties() {
    return ParserOpenRefineMapper.NAME_PROPERTIES;
  }

  @Override
  protected String proposeType() {
    return "Name";
  }

  @Override
  protected String extendValue(String id, OpenRefineModel.ExtendProperty p, MultivaluedMap<String, String> params) {
    NomCode code = settingOr(p, "code", NomCodeParser.PARSER, paramCode(params));
    Rank rank = settingOr(p, "rank", RankParser.PARSER, paramRank(params));
    // re-parse per property: each property may carry its own code/rank in settings, so a single parse-per-id would not honour per-property context
    var pnu = parse(id, code, rank);
    return pnu == null ? null : ParserOpenRefineMapper.nameValue(pnu.getName(), pnu, p.id);
  }

  private ParsedNameUsage parse(String name, NomCode code, Rank rank) {
    return NameParser.PARSER.parse(name, null, rank, code, IssueContainer.VOID).orElse(null);
  }

  private NomCode paramCode(MultivaluedMap<String, String> params) {
    return params == null ? null : SafeParser.parse(NomCodeParser.PARSER, params.getFirst("code")).orNull();
  }

  private Rank paramRank(MultivaluedMap<String, String> params) {
    return params == null ? null : SafeParser.parse(RankParser.PARSER, params.getFirst("rank")).orNull();
  }

  private <T> T settingOr(OpenRefineModel.ExtendProperty p, String key, Parser<T> parser, T fallback) {
    if (p.settings != null && p.settings.get(key) != null) {
      String raw = StringUtils.trimToNull(p.settings.get(key).asText());
      if (raw != null) {
        T v = SafeParser.parse(parser, raw).orNull();
        if (v != null) return v;
      }
    }
    return fallback;
  }
}
