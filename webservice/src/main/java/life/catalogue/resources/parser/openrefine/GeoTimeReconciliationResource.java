package life.catalogue.resources.parser.openrefine;

import life.catalogue.api.vocab.GeoTime;
import life.catalogue.parser.GeoTimeParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import java.net.URI;
import java.util.List;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MultivaluedMap;

@Path("/parser/geotime/reconcile")
public class GeoTimeReconciliationResource extends AbstractParserReconciliationResource {

  public GeoTimeReconciliationResource(URI apiURI, URI clbURI) {
    super(apiURI, clbURI);
  }

  @Override
  protected String typePath() {
    return "geotime";
  }

  @Override
  protected OpenRefineModel.Manifest manifest(String reconcileBaseUrl, String clbBase) {
    return ParserOpenRefineMapper.geoTimeManifest(reconcileBaseUrl, clbBase);
  }

  @Override
  protected OpenRefineModel.Result reconcileSingle(OpenRefineModel.Query q, MultivaluedMap<String, String> params) {
    var result = new OpenRefineModel.Result();
    GeoTime gt = SafeParser.parse(GeoTimeParser.PARSER, q.query).orNull();
    if (gt != null) {
      var c = new OpenRefineModel.Candidate();
      c.id = gt.getName();
      c.name = gt.getName();
      c.score = 100;
      c.match = true;
      c.type.add(ParserOpenRefineMapper.GEOTIME_TYPE);
      result.result.add(c);
    }
    return result;
  }

  @Override
  protected List<OpenRefineModel.ExtendProperty> extendProperties() {
    return ParserOpenRefineMapper.GEOTIME_PROPERTIES;
  }

  @Override
  protected String proposeType() {
    return "GeoTime";
  }

  @Override
  protected String extendValue(String id, OpenRefineModel.ExtendProperty p, MultivaluedMap<String, String> params) {
    return ParserOpenRefineMapper.geoTimeValue(GeoTime.byName(id), p.id);
  }

  @Override
  protected OpenRefineModel.SuggestResponse suggestEntity(String prefix) {
    return ParserOpenRefineMapper.geoTimeSuggest(prefix, SUGGEST_LIMIT);
  }
}
