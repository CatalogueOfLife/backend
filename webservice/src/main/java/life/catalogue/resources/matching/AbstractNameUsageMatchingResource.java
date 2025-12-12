package life.catalogue.resources.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.MatchingConfig;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.*;
import life.catalogue.parser.*;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class AbstractNameUsageMatchingResource {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractNameUsageMatchingResource.class);
  protected static final String DEFAULT_DATASET_KEY = "-99";

  protected final MatchingConfig cfg;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);
  private MatchingUtils utils;

  public AbstractNameUsageMatchingResource(MatchingConfig cfg) {
    this.cfg = cfg;
  }

  public abstract UsageMatcher singleMatchMatcher(int datasetKey);

  /**
   * @param sn will be matched to the nidx here, no need for a matched instance
   */
  private UsageMatchWithOriginal match(int datasetKey, SimpleNameClassified<SimpleNameCached> sn, IssueContainer issues, boolean verbose) {
    UsageMatch match;
    try (var matcher = singleMatchMatcher(datasetKey)) {
      if (utils == null) {
        utils = new MatchingUtils(matcher.getNameIndex());
      }
      match = MatchingJob.interpretAndMatch(sn, sn.getClassification(), issues, verbose, interpreter, utils, matcher);
    }
    return new UsageMatchWithOriginal(match, issues, sn, null);
  }


  private static SimpleNameClassified<SimpleNameCached> interpret(String id,
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
      sn.setClassification(classification.asSimpleNameCached());
    }
    return sn;
  }

  @GET
  public UsageMatchWithOriginal match(@PathParam("key") @DefaultValue(DEFAULT_DATASET_KEY) int datasetKey,
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
    SimpleNameClassified<SimpleNameCached> orig = interpret(id, q, name, sciname, authorship, code, rank, status, classification, issues);
    return match(datasetKey, orig, issues, verbose);
  }

  protected File upload(InputStream data, User user, String suffix) throws IOException {
    File local = cfg.randomUploadFile(user.getUsername().replaceAll("\\s+", "_"), suffix == null ? "" : "." + suffix);
    if (!local.getParentFile().exists()) { 
      local.getParentFile().mkdirs();
    }
    Files.copy(data, local.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return local;
  }

}
