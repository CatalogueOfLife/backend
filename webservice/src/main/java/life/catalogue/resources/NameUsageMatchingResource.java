package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.importer.NameInterpreter;
import life.catalogue.matching.*;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;

@Path("/dataset/{key}/match/nameusage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class NameUsageMatchingResource {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageMatchingResource.class);

  private final WsServerConfig cfg;
  private final JobExecutor exec;
  private final SqlSessionFactory factory;
  private final UsageMatcherGlobal matcher;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  public NameUsageMatchingResource(WsServerConfig cfg, JobExecutor exec, SqlSessionFactory factory, UsageMatcherGlobal matcher) {
    this.cfg = cfg;
    this.exec = exec;
    this.factory = factory;
    this.matcher = matcher;
  }

  private UsageMatchWithOriginal match(int datasetKey, SimpleNameClassified<SimpleName> sn, boolean verbose) {
    if (StringUtils.isBlank(sn.getName())) {
      throw new IllegalArgumentException("Missing name");
    }
    IssueContainer issues = new IssueContainer.Simple();
    return match(datasetKey, sn, issues, verbose);
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


  @GET
  public UsageMatchWithOriginal match(@PathParam("key") int datasetKey,
                                      @QueryParam("id") String id,
                                      @QueryParam("q") String q,
                                      @QueryParam("name") String name,
                                      @QueryParam("scientificName") String sciname,
                                      @QueryParam("authorship") String authorship,
                                      @QueryParam("code") NomCode code,
                                      @QueryParam("rank") Rank rank,
                                      @QueryParam("status") @DefaultValue("ACCEPTED") TaxonomicStatus status,
                                      @QueryParam("verbose") boolean verbose,
                                      @BeanParam Classification classification
  ) throws InterruptedException {
    if (status == TaxonomicStatus.BARE_NAME) {
      throw new IllegalArgumentException("Cannot match a bare name to a name usage");
    }
    SimpleNameClassified<SimpleName> orig = SimpleNameClassified.snc(id, rank, code, status, ObjectUtils.coalesce(sciname, name, q), authorship);
    if (classification != null) {
      orig.setClassification(classification.asSimpleNames());
    }
    return match(datasetKey, orig, verbose);
  }

  private MatchingJob submit(MatchingRequest req, User user) {
    MatchingJob job = new MatchingJob(req, user.getKey(), factory, matcher, cfg);
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
