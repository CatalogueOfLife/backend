package life.catalogue.resources.dataset;

import life.catalogue.api.model.User;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.MatchingConfig;
import life.catalogue.matching.MatchingJob;
import life.catalogue.matching.MatchingRequest;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.resources.ImporterResource;
import life.catalogue.resources.matching.AbstractNameUsageMatchingResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.ibatis.session.SqlSessionFactory;

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
public class NameUsageMatchingResource extends AbstractNameUsageMatchingResource {
  private final JobExecutor exec;
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;

  public NameUsageMatchingResource(MatchingConfig cfg, JobExecutor exec, SqlSessionFactory factory, UsageMatcherFactory matcherFactory) {
    super(cfg);
    this.matcherFactory = matcherFactory;
    this.exec = exec;
    this.factory = factory;
  }

  private MatchingJob submit(MatchingRequest req, User user) throws IOException {
    MatchingJob job = new MatchingJob(req, user.getKey(), factory, matcherFactory, cfg);
    exec.submit(job);
    return job;
  }

  @Override
  public UsageMatcher singleMatchMatcher(int datasetKey) {
    try {
      return matcherFactory.existingOrPostgres(datasetKey);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }


  @POST
  @Path("job")
  public MatchingJob matchSourceJob(@PathParam("key") int datasetKey,
                                            @BeanParam @Valid MatchingRequest req,
                                            @Auth User user) throws IOException {
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
