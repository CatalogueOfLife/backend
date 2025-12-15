package life.catalogue.resources.matching;

import io.dropwizard.auth.Auth;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.config.MatchingConfig;
import life.catalogue.jobs.ReindexSchedulerJob;
import life.catalogue.matching.*;

import life.catalogue.resources.ImporterResource;

import org.glassfish.jersey.server.internal.process.MappableException;

import java.io.*;

@Path("/match/nameusage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class FixedNameUsageMatchingResource extends AbstractNameUsageMatchingResource {
  private final UsageMatcher matcher;
  private final Dataset dataset;
  private final User user = new User(100, "match-service");

  public FixedNameUsageMatchingResource(MatchingConfig cfg, Dataset dataset, UsageMatcher matcher) {
    super(cfg);
    this.dataset = dataset;
    this.matcher =matcher;
  }

  @Override
  public UsageMatcher singleMatchMatcher(int datasetKey) {
    return matcher;
  }

  @POST
  @Consumes({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV, MoreMediaTypes.TEXT_CSV_ALT2, MoreMediaTypes.TEXT_WILDCARD})
  public Response matchTsvJob(@BeanParam @Valid MatchingRequest req,
                              @Context HttpHeaders headers,
                              InputStream data) throws IOException {
    req.setDatasetKey(dataset.getKey());
    req.setUpload(upload(data, user, ImporterResource.contentType2Suffix(headers)));
    validateRequest(req);

    StreamingOutput stream = os -> {
      var job = new StreamingMatchingJob(req, user.getKey(), dataset, matcher, cfg, os);
      try {
        job.runWithLock();
      } catch (Exception e) {
        throw new MappableException(e); // jersey unwraps this before applying exception mappings
      }
    };
    return Response
      .ok(stream)
      .type(MediaType.TEXT_PLAIN_TYPE)
      .build();
  }

  private void validateRequest(MatchingRequest req) {
    if (req.getDatasetKey() != matcher.getDatasetKey()) {
      throw new IllegalArgumentException("This service only matches against dataset "+matcher.getDatasetKey());
    }
    if (req.getSourceDatasetKey() != null) {
      throw new IllegalArgumentException("sourceDatasetKey parameter is not allowed.");
    }
  }
}
