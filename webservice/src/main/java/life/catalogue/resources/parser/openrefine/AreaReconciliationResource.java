package life.catalogue.resources.parser.openrefine;

import life.catalogue.api.vocab.area.Area;
import life.catalogue.parser.AreaParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import java.net.URI;
import java.util.List;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MultivaluedMap;

@Path("/parser/area/reconcile")
public class AreaReconciliationResource extends AbstractParserReconciliationResource {

  public AreaReconciliationResource(URI apiURI, URI clbURI) {
    super(apiURI, clbURI);
  }

  @Override
  protected String typePath() {
    return "area";
  }

  @Override
  protected OpenRefineModel.Manifest manifest(String reconcileBaseUrl, String clbBase) {
    return ParserOpenRefineMapper.areaManifest(reconcileBaseUrl, clbBase);
  }

  @Override
  protected OpenRefineModel.Result reconcileSingle(OpenRefineModel.Query q, MultivaluedMap<String, String> params) {
    return ParserOpenRefineMapper.areaResult(q.query);
  }

  @Override
  protected List<OpenRefineModel.ExtendProperty> extendProperties() {
    return ParserOpenRefineMapper.AREA_PROPERTIES;
  }

  @Override
  protected String proposeType() {
    return "Area";
  }

  @Override
  protected String extendValue(String id, OpenRefineModel.ExtendProperty p, MultivaluedMap<String, String> params) {
    Area a = SafeParser.parse(AreaParser.PARSER, id).orNull();
    return ParserOpenRefineMapper.areaValue(a, p.id);
  }
}
