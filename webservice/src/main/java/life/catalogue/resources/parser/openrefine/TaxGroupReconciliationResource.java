package life.catalogue.resources.parser.openrefine;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;
import life.catalogue.resources.matching.openrefine.OpenRefineQueries;

import java.net.URI;
import java.util.List;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MultivaluedMap;

@Path("/parser/taxgroup/reconcile")
public class TaxGroupReconciliationResource extends AbstractParserReconciliationResource {
  private final TaxGroupAnalyzer analyzer = new TaxGroupAnalyzer();

  public TaxGroupReconciliationResource(URI apiURI, URI clbURI) {
    super(apiURI, clbURI);
  }

  @Override
  protected String typePath() {
    return "taxgroup";
  }

  @Override
  protected OpenRefineModel.Manifest manifest(String reconcileBaseUrl, String clbBase) {
    return ParserOpenRefineMapper.taxGroupManifest(reconcileBaseUrl, clbBase);
  }

  @Override
  protected OpenRefineModel.Result reconcileSingle(OpenRefineModel.Query q, MultivaluedMap<String, String> params) {
    var result = new OpenRefineModel.Result();
    var sn = OpenRefineQueries.toSimpleName(q);
    if (sn != null) {
      TaxGroup g = analyzer.analyze(sn, sn.getClassification() == null ? java.util.List.of() : sn.getClassification());
      if (g != null && !g.isOther()) {
        var c = new OpenRefineModel.Candidate();
        c.id = g.name();
        c.name = g.name();
        c.score = 100;
        c.match = true;
        c.type.add(ParserOpenRefineMapper.TAXGROUP_TYPE);
        result.result.add(c);
      }
    }
    return result;
  }

  @Override
  protected List<OpenRefineModel.ExtendProperty> extendProperties() {
    return ParserOpenRefineMapper.TAXGROUP_PROPERTIES;
  }

  @Override
  protected String proposeType() {
    return "TaxGroup";
  }

  @Override
  protected String extendValue(String id, OpenRefineModel.ExtendProperty p, MultivaluedMap<String, String> params) {
    return ParserOpenRefineMapper.taxGroupValue(parse(id), p.id);
  }

  @Override
  protected OpenRefineModel.SuggestResponse suggestEntity(String prefix) {
    return ParserOpenRefineMapper.taxGroupSuggest(prefix, SUGGEST_LIMIT);
  }

  private static TaxGroup parse(String id) {
    if (id == null) return null;
    try {
      return TaxGroup.valueOf(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
