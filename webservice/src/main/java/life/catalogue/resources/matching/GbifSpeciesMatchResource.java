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

import org.gbif.nameparser.api.Rank;

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
    // GBIF's parameter names first, the CLB ones of /match/nameusage are accepted as aliases
    String author = StringUtils.firstNonBlank(scientificNameAuthorship, authorship);
    String rnk = StringUtils.firstNonBlank(taxonRank, verbatimTaxonRank, rank);
    Classification cl = classification == null ? new Classification() : new Classification(classification);
    String name = scientificName;
    if (StringUtils.isBlank(name)) {
      // like GBIF the genus of the classification completes a name given as epithets only
      String genus = StringUtils.firstNonBlank(genericName, StringUtils.isBlank(specificEpithet) ? null : cl.getGenus());
      name = joinEpithets(genus, specificEpithet, infraspecificEpithet);
    }
    if (StringUtils.isBlank(name) && !cl.isEmpty()) {
      // no name at all: match the lowest taxon of the classification, as GBIF does
      Rank lowest = cl.getLowestExistingRank();
      name = cl.getByRank(lowest);
      rnk = lowest.name();
      cl.clearRankAndBelow(lowest);
    }
    if (StringUtils.isBlank(name)) {
      return GbifMatchMapper.map(null, UsageMatch.empty(matcher.getDatasetKey()), verbose, System.currentTimeMillis() - start);
    }

    IssueContainer issues = new IssueContainer.Simple();
    var query = interpret(name, author, code, rnk, cl, issues);
    UsageMatch m = query == null ? UsageMatch.empty(matcher.getDatasetKey()) : matcher.match(query, false, verbose, !strict);
    String note = null;
    // Provisional: a supplied rank that contradicts the name, a subspecies sent as SPECIES or a genus as SPECIES,
    // keeps the matcher from finding the name. If so retry with the rank the parser reads from the name.
    // The matcher itself should weigh such a rank, see the GBIF impact report.
    if (query != null && rnk != null && !isExactOrVariant(m)) {
      var parsed = interpret(name, author, code, null, cl, new IssueContainer.Simple());
      if (parsed != null && parsed.getRank() != query.getRank()) {
        UsageMatch m2 = matcher.match(parsed, false, verbose, !strict);
        if (isExactOrVariant(m2) || (!m.isMatch() && m2.isMatch())) {
          note = "taxonRank " + rnk + " ignored as it contradicts the name";
          m = m2;
          query = parsed;
        }
      }
    }
    var gm = GbifMatchMapper.map(query, m, verbose, System.currentTimeMillis() - start);
    if (note != null) {
      gm.diagnostics.note = gm.diagnostics.note == null ? note : gm.diagnostics.note + "; " + note;
    }
    return gm;
  }

  private static boolean isExactOrVariant(UsageMatch m) {
    return m.isMatch() && (m.type == MatchType.EXACT || m.type == MatchType.VARIANT);
  }

  /**
   * @return the parsed name matched to the names index as the usage matcher expects it, null if it cannot be parsed
   */
  @Nullable
  private SimpleNameClassified<SimpleNameCached> interpret(String name, String author, String code, @Nullable String rank,
                                                          Classification cl, IssueContainer issues) {
    var sn = AbstractNameUsageMatchingResource.interpret(null, null, null, name, author, code, rank, null, cl, issues);
    return MatchingJob.interpret(sn, sn.getClassification(), issues, interpreter, utils).orElse(null);
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
