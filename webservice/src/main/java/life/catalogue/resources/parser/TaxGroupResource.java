package life.catalogue.resources.parser;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.TaxGroupAnalyzer;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/parser/taxgroup")
@Produces(MediaType.APPLICATION_JSON)
public class TaxGroupResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TaxGroupResource.class);
  private final TaxGroupAnalyzer groupAnalyzer = new TaxGroupAnalyzer();


  public static class GroupResult {
    public final SimpleNameClassified<SimpleName> name;
    public final TaxGroup group;

    public GroupResult(SimpleNameClassified<SimpleName> name, TaxGroup group) {
      this.name = name;
      this.group = group;
    }
  }

  @GET
  public GroupResult group(@QueryParam("id") String id,
                            @QueryParam("q") String q,
                            @QueryParam("name") String name,
                            @QueryParam("scientificName") String sciname,
                            @QueryParam("authorship") String authorship,
                            @QueryParam("code") NomCode code,
                            @QueryParam("rank") Rank rank,
                            @BeanParam Classification classification
  ) {
    SimpleNameClassified<SimpleName> orig = SimpleNameClassified.snc(id, rank, code, TaxonomicStatus.ACCEPTED, ObjectUtils.coalesce(sciname, name, q), authorship);
    if (classification != null) {
      orig.setClassification(classification.asSimpleNames());
    }
    var group = groupAnalyzer.analyze(orig, orig.getClassification());
    return new GroupResult(orig, group);
  }

}
