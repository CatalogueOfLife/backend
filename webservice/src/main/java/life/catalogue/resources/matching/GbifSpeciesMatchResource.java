package life.catalogue.resources.matching;

import life.catalogue.api.model.*;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.resources.matching.gbif.GbifNameUsageMatch;
import life.catalogue.resources.matching.gbif.GbifSpeciesMatcher;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.annotation.Nullable;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * A GBIF compatible species match service, mimicking the v2 API of https://github.com/gbif/matching-ws
 * on top of the single persistent usage matcher of a matching server.
 * Not supported are fuzzy matching, the exclude, scientificNameID and taxonConceptID parameters,
 * IUCN additionalStatus and the nested set left/right values.
 * The main and the bundle server offer the same per dataset under {@link DatasetGbifMatchResource}.
 */
@Path("/v2/species/match")
@Produces(MediaType.APPLICATION_JSON)
public class GbifSpeciesMatchResource {
  private final Dataset dataset;
  private final UsageMatcher matcher;
  @Nullable
  private final UUID checklistKey;
  private final LocalDateTime created = LocalDateTime.now();
  private final GbifSpeciesMatcher gbif;

  /**
   * @param checklistKey the GBIF dataset key requests are allowed to address, falls back to the dataset's gbifKey
   */
  public GbifSpeciesMatchResource(Dataset dataset, UsageMatcher matcher, @Nullable UUID checklistKey) {
    this.dataset = dataset;
    this.matcher = matcher;
    this.checklistKey = checklistKey != null ? checklistKey : dataset.getGbifKey();
    this.gbif = new GbifSpeciesMatcher(matcher.getNameIndex());
  }

  @GET
  public GbifNameUsageMatch match(@QueryParam("checklistKey") UUID checklistKey,
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
    if (checklistKey != null && !checklistKey.equals(this.checklistKey)) {
      throw new IllegalArgumentException("This service only matches against checklist " + this.checklistKey);
    }
    return gbif.match(matcher, usageKey, taxonID, scientificName, scientificNameAuthorship, authorship, taxonRank,
      verbatimTaxonRank, rank, code, genericName, specificEpithet, infraspecificEpithet, strict, verbose, classification);
  }

  @GET
  @Path("metadata")
  public GbifNameUsageMatch.Metadata metadata() {
    return GbifSpeciesMatcher.metadata(dataset, matcher, checklistKey, created);
  }
}
