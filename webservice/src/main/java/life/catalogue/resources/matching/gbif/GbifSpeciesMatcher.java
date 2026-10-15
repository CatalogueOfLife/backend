package life.catalogue.resources.matching.gbif;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.MatchingJob;
import life.catalogue.matching.MatchingUtils;
import life.catalogue.matching.NameAssembler;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.parser.NameParser;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.resources.matching.AbstractNameUsageMatchingResource;

import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * The GBIF v2 species match of https://github.com/gbif/matching-ws on top of a CLB usage matcher.
 * Shared by the resources serving it: the GBIF path of the matching server and the dataset scoped one of the
 * main and the bundle server. It only translates request and response, the matching is the usage matcher's.
 */
public class GbifSpeciesMatcher {
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);
  private final MatchingUtils utils;

  /**
   * @param nameIndex the names index all matchers of the server use
   */
  public GbifSpeciesMatcher(NameIndex nameIndex) {
    this.utils = new MatchingUtils(nameIndex);
  }

  public GbifNameUsageMatch match(UsageMatcher matcher,
                                  String usageKey,
                                  String taxonID,
                                  String scientificName,
                                  String scientificNameAuthorship,
                                  String authorship,
                                  String taxonRank,
                                  String verbatimTaxonRank,
                                  String rank,
                                  String code,
                                  String genericName,
                                  String specificEpithet,
                                  String infraspecificEpithet,
                                  boolean strict,
                                  boolean verbose,
                                  @Nullable Classification classification
  ) {
    final long start = System.currentTimeMillis();
    String id = StringUtils.firstNonBlank(usageKey, taxonID);
    if (id != null) {
      return GbifMatchMapper.map(null, lookup(matcher, id), verbose, System.currentTimeMillis() - start);
    }
    // GBIF's parameter names first, the CLB ones of /match/nameusage are accepted as aliases
    String author = StringUtils.firstNonBlank(scientificNameAuthorship, authorship);
    String rnk = StringUtils.firstNonBlank(taxonRank, verbatimTaxonRank, rank);
    Classification cl = classification == null ? new Classification() : new Classification(classification);
    // like GBIF build the name from all its pieces: an abbreviated genus, epithets only, or else the classification
    Rank parsedRank = SafeParser.parse(RankParser.PARSER, rnk).orNull();
    var assembled = NameAssembler.build(scientificName, author, genericName, specificEpithet, infraspecificEpithet, parsedRank, cl);
    String name = assembled.name();
    if (assembled.fromClassification()) {
      // no name at all: match the lowest taxon of the classification, as GBIF does
      rnk = assembled.rank().name();
      cl.clearRankAndBelow(assembled.rank());
    }
    if (StringUtils.isBlank(name)) {
      return GbifMatchMapper.map(null, UsageMatch.empty(matcher.getDatasetKey()), verbose, System.currentTimeMillis() - start);
    }

    // GBIF's strict parameter is the STRICT matching mode
    final MatchingMode mode = strict ? MatchingMode.STRICT : MatchingMode.DEFAULT;
    IssueContainer issues = new IssueContainer.Simple();
    // the assembled name carries the authorship
    var query = interpret(name, null, code, rnk, cl, issues);
    UsageMatch m = query == null ? UsageMatch.empty(matcher.getDatasetKey()) : matcher.match(query, false, verbose, mode.higherRank());
    String note = null;
    // Provisional: a supplied rank that contradicts the name, a subspecies sent as SPECIES or a genus as SPECIES,
    // keeps the matcher from finding the name. If so retry with the rank the parser reads from the name.
    // The matcher itself should weigh such a rank, see the GBIF impact report. A strict match keeps the rank.
    if (mode != MatchingMode.STRICT && query != null && !isExactOrVariant(m) && rankContradictsName(name, query.getRank())) {
      var parsed = interpret(name, null, code, null, cl, new IssueContainer.Simple());
      if (parsed != null) {
        UsageMatch m2 = matcher.match(parsed, false, verbose, mode.higherRank());
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

  /**
   * True if the given rank contradicts the rank the parser reads from the name alone: a trinomial or a binomial
   * sent with another rank, or a single word sent as SPECIES or lower. A name the parser cannot rank is only
   * contradicted by a species or lower rank.
   */
  static boolean rankContradictsName(String name, @Nullable Rank given) {
    if (given == null) {
      return false;
    }
    Rank parsed = NameParser.PARSER.parse(name).map(pnu -> pnu.getName().getRank()).orElse(null);
    if (parsed == null || parsed == Rank.UNRANKED || parsed == Rank.OTHER) {
      return given.isSpeciesOrBelow();
    }
    if (parsed == Rank.INFRASPECIFIC_NAME) {
      return !given.isInfraspecific();
    }
    return parsed != given;
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

  /**
   * @param checklistKey the GBIF dataset key of the matched checklist, if known
   * @param created when the service started
   */
  public static GbifNameUsageMatch.Metadata metadata(Dataset dataset, UsageMatcher matcher, @Nullable UUID checklistKey,
                                                     LocalDateTime created) {
    var md = new GbifNameUsageMatch.Metadata();
    md.created = created.toString();
    md.mainIndex.clbDatasetKey = String.valueOf(dataset.getKey());
    md.mainIndex.datasetKey = checklistKey == null ? null : checklistKey.toString();
    md.mainIndex.datasetAlias = dataset.getAlias();
    md.mainIndex.datasetTitle = dataset.getTitle();
    md.mainIndex.nameUsageCount = matcher.store().size();
    return md;
  }

  private static UsageMatch lookup(UsageMatcher matcher, String id) {
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
}
