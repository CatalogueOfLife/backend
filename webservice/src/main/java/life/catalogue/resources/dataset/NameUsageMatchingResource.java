package life.catalogue.resources.dataset;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.*;
import life.catalogue.parser.*;
import life.catalogue.resources.ImporterResource;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/dataset/{key}/match/nameusage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class NameUsageMatchingResource {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageMatchingResource.class);

  private final WsServerConfig cfg;
  private final JobExecutor exec;
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  public NameUsageMatchingResource(WsServerConfig cfg, JobExecutor exec, SqlSessionFactory factory, UsageMatcherFactory matcherFactory) {
    this.cfg = cfg;
    this.exec = exec;
    this.factory = factory;
    this.matcherFactory = matcherFactory;
  }

  private UsageMatchWithOriginal match(int datasetKey, SimpleNameClassified<SimpleNameCached> sn, IssueContainer issues, boolean verbose) {
    UsageMatch match;
    var opt = interpreter.interpret(sn, issues);
    if (opt.isPresent()) {
      NameUsageBase nu = (NameUsageBase) NameUsage.create(sn.getStatus(), opt.get().getName());
      // replace name parsers unranked with null to let the matcher know its coming from outside
      if (nu.getRank() == Rank.UNRANKED) {
        nu.getName().setRank(null);
      }
      try (var matcher = matcherFactory.existingOrPostgres(datasetKey)) {
        match = matcher.match(sn, false, verbose);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      match = UsageMatch.empty(0);
      issues.add(Issue.UNPARSABLE_NAME);
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
    SimpleNameClassified<SimpleNameCached> orig = interpret(id, q, name, sciname, authorship, code, rank, status, classification, issues);
    return match(datasetKey, orig, issues, verbose);
  }

  private MatchingJob submit(MatchingRequest req, User user) {
    MatchingJob job = new MatchingJob(req, user.getKey(), factory, matcherFactory, cfg.getNormalizerConfig());
    exec.submit(job);
    return job;
  }

  private File upload(InputStream data, User user, String suffix) throws IOException {
    File local = cfg.normalizer.uploadFile(user.getUsername().replaceAll("\\s+", "_"), suffix == null ? "" : "." + suffix);
    if (!local.getParentFile().exists()) { 
      local.getParentFile().mkdirs();
    }
    Files.copy(data, local.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return local;
  }

  @POST
  @Path("job")
  public MatchingJob matchSourceJob(@PathParam("key") int datasetKey,
                                    @BeanParam @Valid MatchingRequest req,
                                    @Auth User user) {
    req.setDatasetKey(datasetKey);
    if (req.getSourceDatasetKey() == null) {
      throw new IllegalArgumentException("sourceDatasetKey parameter or CSV/TSV data upload required");
    }
    return submit(req, user);
  }

  @POST
  @Path("job")
  @Consumes({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV, MoreMediaTypes.TEXT_CSV_ALT2, MoreMediaTypes.TEXT_WILDCARD})
  public MatchingJob matchTsvJob(@PathParam("key") int datasetKey,
                                 @BeanParam @Valid MatchingRequest req,
                                 @Context HttpHeaders headers,
                                  InputStream data,
                                  @Auth User user) throws IOException {
    req.setDatasetKey(datasetKey);
    req.setUpload(upload(data, user, ImporterResource.contentType2Suffix(headers)));
    return submit(req, user);
  }

}
