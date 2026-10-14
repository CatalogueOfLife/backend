package life.catalogue.resources.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.MatchingJob;
import life.catalogue.matching.MatchingUtils;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.resources.matching.gbif.GbifMatchMapper;
import life.catalogue.resources.matching.gbif.GbifNameUsageMatch;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * A GBIF compatible species match service, mimicking the v2 API of https://github.com/gbif/matching-ws
 * on top of the single persistent usage matcher of a matching server.
 * Not supported are fuzzy matching, the exclude, scientificNameID and taxonConceptID parameters,
 * IUCN additionalStatus and the nested set left/right values.
 */
@Path("/v2/species/match")
@Produces(MediaType.APPLICATION_JSON)
public class GbifSpeciesMatchResource {
  private final Dataset dataset;
  private final UsageMatcher matcher;
  @Nullable
  private final UUID checklistKey;
  private final LocalDateTime created = LocalDateTime.now();
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);
  private final MatchingUtils utils;

  /**
   * @param checklistKey the GBIF dataset key requests are allowed to address, falls back to the dataset's gbifKey
   */
  public GbifSpeciesMatchResource(Dataset dataset, UsageMatcher matcher, @Nullable UUID checklistKey) {
    this.dataset = dataset;
    this.matcher = matcher;
    this.checklistKey = checklistKey != null ? checklistKey : dataset.getGbifKey();
    this.utils = new MatchingUtils(matcher.getNameIndex());
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
    final long start = System.currentTimeMillis();
    String id = StringUtils.firstNonBlank(usageKey, taxonID);
    if (id != null) {
      return GbifMatchMapper.map(null, lookup(id), verbose, System.currentTimeMillis() - start);
    }
    String name = StringUtils.isBlank(scientificName) ? joinEpithets(genericName, specificEpithet, infraspecificEpithet) : scientificName;
    IssueContainer issues = new IssueContainer.Simple();
    // GBIF's parameter names first, the CLB ones of /match/nameusage are accepted as aliases
    String author = StringUtils.firstNonBlank(scientificNameAuthorship, authorship);
    String rnk = StringUtils.firstNonBlank(taxonRank, verbatimTaxonRank, rank);
    var sn = AbstractNameUsageMatchingResource.interpret(null, null, null, name, author, code, rnk, null, classification, issues);
    UsageMatch m = MatchingJob.interpretAndMatch(sn, sn.getClassification(), issues, verbose, !strict, interpreter, utils, matcher);
    return GbifMatchMapper.map(sn, m, verbose, System.currentTimeMillis() - start);
  }

  @GET
  @Path("metadata")
  public GbifNameUsageMatch.Metadata metadata() {
    var md = new GbifNameUsageMatch.Metadata();
    md.created = created.toString();
    md.mainIndex.clbDatasetKey = String.valueOf(dataset.getKey());
    md.mainIndex.datasetKey = checklistKey == null ? null : checklistKey.toString();
    md.mainIndex.datasetAlias = dataset.getAlias();
    md.mainIndex.datasetTitle = dataset.getTitle();
    md.mainIndex.nameUsageCount = matcher.store().size();
    return md;
  }

  private UsageMatch lookup(String id) {
    try {
      var u = matcher.store().getSNClassified(id);
      if (u != null) {
        return UsageMatch.match(MatchType.EXACT, u, matcher.getDatasetKey(), null);
      }
    } catch (NotFoundException e) {
      // no such usage
    }
    return UsageMatch.empty(matcher.getDatasetKey());
  }

  /**
   * @return the epithets joined into a name, null if there are none
   */
  static String joinEpithets(String genericName, String specificEpithet, String infraspecificEpithet) {
    String name = Stream.of(genericName, specificEpithet, infraspecificEpithet)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(Collectors.joining(" "));
    return name.isEmpty() ? null : name;
  }
}
