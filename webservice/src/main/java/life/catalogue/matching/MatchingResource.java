package life.catalogue.matching;

import io.dropwizard.auth.Auth;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.parser.*;
import life.catalogue.resources.ImporterResource;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Path("/dataset/{key}/match/nameusage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class MatchingResource {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingResource.class);

  private final MatchingConfig cfg;
  private final SqlSessionFactory factory;
  private final UsageMatcherGlobal matcher;
  private final UsageCache uCache;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  public MatchingResource(MatchingConfig cfg, UsageMatcherGlobal matcher) {
    this.cfg = cfg;
    this.factory = factory;
    this.matcher = matcher;
    this.uCache = matcher.getUCache();
  }

  private UsageMatchWithOriginal match(int datasetKey, SimpleNameClassified<SimpleName> sn, IssueContainer issues, boolean verbose) {
    UsageMatch match;
    var opt = interpreter.interpret(sn, issues);
    if (opt.isPresent()) {
      NameUsageBase nu = (NameUsageBase) NameUsage.create(sn.getStatus(), opt.get().getName());
      // replace name parsers unranked with null to let the matcher know its coming from outside
      if (nu.getRank() == Rank.UNRANKED) {
        nu.getName().setRank(null);
      }
      match = matcher.match(datasetKey, nu, sn.getClassification(), false, verbose);
    } else {
      match = UsageMatch.empty(0);
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
  public UsageMatchWithOriginal match(@PathParam("key") int datasetKey,
                                      @QueryParam("id") String id,
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
    return match(datasetKey, orig, issues, verbose);
  }


}
