package life.catalogue.resources.parser;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.metadata.DoiResolver;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/parser/reference")
@Produces({MediaType.APPLICATION_JSON, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML, MoreMediaTypes.APP_JSON_CSL, MoreMediaTypes.APP_BIBTEX})
public class ReferenceParserResource {
  private final DoiResolver resolver;

  public ReferenceParserResource(DoiResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * Look up DOI metadata in CrossRef
   */
  @GET
  @Path("resolve")
  public Object resolveDOI(@QueryParam("doi") DOI doi) throws Exception {
    IssueContainer issues = IssueContainer.simple();
    var cit = resolver.resolve(doi, issues);
    if (cit == null) {
      return issues;
    }
    return cit;
  }

}
