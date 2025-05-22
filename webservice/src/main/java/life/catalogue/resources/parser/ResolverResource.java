package life.catalogue.resources.parser;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.vocab.Issue;
import life.catalogue.metadata.DoiResolver;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/resolver")
@Produces(MediaType.APPLICATION_JSON)
public class ResolverResource {
  private final DoiResolver doiResolver;

  public ResolverResource(DoiResolver doiResolver) {
    this.doiResolver = doiResolver;
  }

  public static class Result<T> {
    public final String original;
    public final T value;
    public final IssueContainer issues;

    public Result(String original, T value, IssueContainer issues) {
      this.original = original;
      this.value = value;
      this.issues = issues;
    }
  }

  @GET
  @Path("doi")
  public Result<Citation> doi(@QueryParam("q") String q) {
    var issues = new IssueContainer.Simple();
    var doi = DOI.parse(q);
    if (doi.isPresent()) {
      var val = doiResolver.resolve(doi.get(), issues);
      return new Result<>(q, val, issues);
    }
    issues.add(Issue.DOI_INVALID);
    return new Result<>(q, null, issues);
  }

  
}
