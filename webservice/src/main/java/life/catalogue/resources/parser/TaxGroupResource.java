package life.catalogue.resources.parser;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.assembly.TaxGroupAnalyzer;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.matching.UsageMatchWithOriginal;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.BasionymGroup;
import life.catalogue.matching.authorship.BasionymSorter;
import life.catalogue.parser.NameParser;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
