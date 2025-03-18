package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.parser.*;

import org.apache.commons.lang3.time.StopWatch;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class MatchingResource {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingResource.class);

  private final MatchingStorageChrononicle storage;
  private final MatchingService<SimpleNameCached> matcher;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  public MatchingResource(MatchingStorageChrononicle storage) {
    this.matcher = storage.newMatchingService();
    this.storage = storage;
  }

  private UsageMatchWithOriginal match(SimpleNameClassified<SimpleName> sn, IssueContainer issues, boolean verbose) {
    UsageMatch match;
    var opt = interpreter.interpret(sn, issues);
    if (opt.isPresent()) {
      NameUsageBase nu = (NameUsageBase) NameUsage.create(sn.getStatus(), opt.get().getName());
      // replace name parsers unranked with null to let the matcher know its coming from outside
      if (nu.getRank() == Rank.UNRANKED) {
        nu.getName().setRank(null);
      }
      match = matcher.match(nu, sn.getClassification(), false, verbose);
    } else {
      match = UsageMatch.empty();
      issues.addIssue(Issue.UNPARSABLE_NAME);
    }
    return new UsageMatchWithOriginal(match, issues, sn);
  }


  private static SimpleNameClassified<SimpleName> interpret(String id,
                                                            String q,
                                                            String name,
                                                            String sciname,
                                                            String authorship,
                                                            String code,
                                                            String rank,
                                                            String status,
                                                            Classification classification,
                                                            IssueContainer issues) {
    NomCode iCode = SafeParser.parse(NomCodeParser.PARSER, code)
      .orNull(Issue.NOMENCLATURAL_CODE_INVALID, issues);

    Rank iRank = SafeParser.parse(RankParser.PARSER, rank)
      .orNull(Issue.RANK_INVALID, issues);

    EnumNote<TaxonomicStatus> iStatus = SafeParser.parse(TaxonomicStatusParser.PARSER, status)
      .orElse(() -> new EnumNote<>(TaxonomicStatus.ACCEPTED, null), Issue.TAXONOMIC_STATUS_INVALID, issues);

    var sn = SimpleNameClassified.snc(id, iRank, iCode, iStatus.val, ObjectUtils.coalesce(sciname, name, q), authorship);
    if (StringUtils.isBlank(sn.getName())) {
      throw new IllegalArgumentException("Missing name");
    }
    if (classification != null) {
      sn.setClassification(classification.asSimpleNames());
    }
    return sn;
  }


  @GET
  @Path("/metadata")
  public MatchingStorageMetadata metadata() throws InterruptedException {
    return storage.metadata();
  }

  @GET
  @Path("/match")
  public UsageMatchWithOriginal match(@QueryParam("id") String id,
                                      @QueryParam("q") String q,
                                      @QueryParam("name") String name,
                                      @QueryParam("scientificName") String sciname,
                                      @QueryParam("authorship") String authorship,
                                      @QueryParam("code") String code,
                                      @QueryParam("rank") String rank,
                                      @QueryParam("status") String status,
                                      @QueryParam("verbose") boolean verbose,
                                      @BeanParam Classification classification
  ) throws InterruptedException {
    IssueContainer issues = new IssueContainer.Simple();
    SimpleNameClassified<SimpleName> orig = interpret(id, q, name, sciname, authorship, code, rank, status, classification, issues);
    return match(orig, issues, verbose);
  }

  @GET
  @Path("/v2/species/match/metadata")
  public MatchingStorageMetadata metadata2() throws InterruptedException {
    return storage.metadata();
  }

  @GET
  @Path("/v2/species/match")
  public NameUsageMatch match2(@QueryParam("usageKey") String usageKey,

                                       @QueryParam("taxonID") String taxonID,
                                       @QueryParam("taxonConceptID") String taxonConceptID,
                                       @QueryParam("scientificNameID") String scientificNameID,

                                       @QueryParam("scientificName") String scientificName,
                                       @QueryParam("name") String scientificName2,

                                       @QueryParam("scientificNameAuthorship") String authorship,
                                       @QueryParam("authorship") String authorship2,

                                       @QueryParam("taxonRank") String rank,
                                       @QueryParam("rank") String rank2,

                                       @QueryParam("code") String code,

                                       @QueryParam("genericName") String genericName,
                                       @QueryParam("specificEpithet") String specificEpithet,
                                       @QueryParam("infraspecificEpithet") String infraspecificEpithet,

                                       @BeanParam Classification classification,

                                       @QueryParam("exclude") Set<String> exclude,
                                       @QueryParam("strict") boolean strict,
                                       @QueryParam("verbose") boolean verbose
  ) throws InterruptedException {

    StopWatch watch = new StopWatch();
    watch.start();

    IssueContainer issues = new IssueContainer.Simple();
    SimpleNameClassified<SimpleName> orig = interpret(taxonID, scientificName, scientificName2, null,
      ObjectUtils.coalesce(authorship, authorship2), code, ObjectUtils.coalesce(rank, rank2), null, classification, issues
    );
    var m = match(orig, issues, verbose);

    return convert(m);
  }

  private NameUsageMatch convert(UsageMatchWithOriginal m) {
    var um = new NameUsageMatch();
    um.setDiagnostics(new NameUsageMatch.Diagnostics());
    um.getDiagnostics().setMatchType(m.type);
    if (m.issues.hasIssues()) {
      um.getDiagnostics().setIssues(new ArrayList<>(m.issues.getIssues()));
    }
    if (m.isMatch()) {
      um.getDiagnostics().setStatus(m.usage.getStatus());
      um.setUsage(storage.getParsedUsage(m.getId()));
      um.setClassification(m.usage.getClassification());
      if (um.isSynonym()) {
        um.setAcceptedUsage(storage.getParsedUsage(um.getUsage().getParentKey()));
      }
    }
    if (m.alternatives != null) {
      um.getDiagnostics().setAlternatives(m.alternatives.stream()
        .map( alt -> {
          var am = new NameUsageMatch();
          am.setUsage(storage.getParsedUsage(alt.getId()));
          am.setClassification(m.usage.getClassification());
          if (am.isSynonym()) {
            am.setAcceptedUsage(storage.getParsedUsage(am.getUsage().getParentKey()));
          }
          return am;
        })
        .collect(Collectors.toUnmodifiableList()));
    }
    return um;
  }
}
