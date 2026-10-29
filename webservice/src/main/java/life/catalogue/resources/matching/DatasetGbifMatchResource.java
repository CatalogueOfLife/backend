package life.catalogue.resources.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Classification;
import life.catalogue.api.model.Dataset;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.resources.matching.gbif.GbifNameUsageMatch;
import life.catalogue.resources.matching.gbif.GbifSpeciesMatcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * The GBIF compatible species match of {@link GbifSpeciesMatchResource} against any dataset with a persistent
 * matcher store. The dataset is given by the path, so GBIF's checklistKey parameter is not read.
 * Other than /match/nameusage it never matches against live postgres data nor schedules a matcher build:
 * the service is meant for occurrence scale traffic, so a dataset without a prebuilt store - every project and
 * most private datasets - is a 404.
 */
@Path("/dataset/{key}/match/gbif")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetGbifMatchResource {
  private final UsageMatcherFactory matcherFactory;
  private final SqlSessionFactory factory;
  private final GbifSpeciesMatcher gbif;
  private final LocalDateTime created = LocalDateTime.now();

  public DatasetGbifMatchResource(UsageMatcherFactory matcherFactory, SqlSessionFactory factory) {
    this.matcherFactory = matcherFactory;
    this.factory = factory;
    this.gbif = new GbifSpeciesMatcher(matcherFactory.getNameIndex());
  }

  @GET
  public GbifNameUsageMatch match(@PathParam("key") int datasetKey,
                                  @QueryParam("usageKey") String usageKey,
                                  @QueryParam("taxonID") String taxonID,
                                  @QueryParam("scientificName") String scientificName,
                                  @QueryParam("scientificNameAuthorship") String scientificNameAuthorship,
                                  @QueryParam("authorship") String authorship,
                                  @QueryParam("taxonRank") String taxonRank,
                                  @QueryParam("verbatimTaxonRank") String verbatimTaxonRank,
                                  @QueryParam("rank") String rank,
                                  @QueryParam("code") String code,
                                  @QueryParam("genericName") String genericName,
                                  @QueryParam("specificEpithet") String specificEpithet,
                                  @QueryParam("infraspecificEpithet") String infraspecificEpithet,
                                  @QueryParam("strict") boolean strict,
                                  @QueryParam("verbose") boolean verbose,
                                  @BeanParam Classification classification
  ) {
    try (UsageMatcher matcher = fileMatcher(datasetKey)) {
      return gbif.match(matcher, usageKey, taxonID, scientificName, scientificNameAuthorship, authorship, taxonRank,
        verbatimTaxonRank, rank, code, genericName, specificEpithet, infraspecificEpithet, strict, verbose, classification);
    }
  }

  @GET
  @Path("metadata")
  public GbifNameUsageMatch.Metadata metadata(@PathParam("key") int datasetKey) {
    Dataset d;
    try (SqlSession session = factory.openSession()) {
      d = session.getMapper(DatasetMapper.class).get(datasetKey);
    }
    if (d == null) {
      throw NotFoundException.notFound(Dataset.class, datasetKey);
    }
    try (UsageMatcher matcher = fileMatcher(datasetKey)) {
      return GbifSpeciesMatcher.metadata(d, matcher, d.getGbifKey(), created);
    }
  }

  /**
   * @return the persistent matcher of the dataset, to be closed after use
   * @throws NotFoundException if the dataset has no persistent matcher store
   */
  private UsageMatcher fileMatcher(int datasetKey) {
    UsageMatcher m;
    try {
      m = matcherFactory.get(datasetKey);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (m == null) {
      throw new NotFoundException(datasetKey, "Dataset " + datasetKey + " has no persistent matcher. GBIF matching is only " +
        "offered for datasets with one, which every published dataset above a minimum size has; use /dataset/" + datasetKey +
        "/match/nameusage instead");
    }
    return m;
  }
}
