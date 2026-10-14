package life.catalogue.resources.matching.gbif;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.resources.matching.openrefine.OpenRefineMapper;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Pure mapping from a CLB usage match to the response of the GBIF v2 species match service.
 * Kept free of HTTP concerns so it can be unit tested in isolation.
 */
public class GbifMatchMapper {

  private GbifMatchMapper() {}

  /**
   * @param query the interpreted name the match was made for, used to classify picked ambiguous matches and alternatives
   * @param verbose if true alternatives are included
   * @param millis time the match took
   */
  public static GbifNameUsageMatch map(@Nullable SimpleName query, UsageMatch m, boolean verbose, long millis) {
    var gm = new GbifNameUsageMatch();
    gm.diagnostics.timeTaken = millis;
    MatchType type = m == null || m.type == null ? MatchType.NONE : m.type;
    if (m != null && m.isMatch()) {
      setUsage(gm, m.usage);
      gm.diagnostics.matchType = gbifType(type == MatchType.AMBIGUOUS ? labelType(query, m.usage) : type);
    } else {
      gm.diagnostics.matchType = gbifType(MatchType.NONE);
    }
    gm.diagnostics.confidence = confidence(type);
    if (gm.diagnostics.matchType == null || !gm.diagnostics.matchType.equals(type.name())) {
      gm.diagnostics.note = "clb matchType=" + type;
    }
    if (verbose && m != null && m.alternatives != null && !m.alternatives.isEmpty()) {
      gm.diagnostics.alternatives = new ArrayList<>();
      for (var alt : m.alternatives) {
        var ga = new GbifNameUsageMatch();
        setUsage(ga, alt);
        ga.diagnostics.matchType = gbifType(labelType(query, alt));
        gm.diagnostics.alternatives.add(ga);
      }
    }
    return gm;
  }

  /**
   * A confidence of 0-100 derived from the CLB match type alone, using the OpenRefine scores.
   * No match is a confident answer, just as GBIF reports it.
   */
  static int confidence(MatchType type) {
    if (type == null || type == MatchType.NONE || type == MatchType.UNSUPPORTED) {
      return 100;
    }
    return (int) OpenRefineMapper.score(type);
  }

  /**
   * GBIF has no CANONICAL, AMBIGUOUS or UNSUPPORTED match type.
   * A canonical match ignores a missing authorship, which GBIF considers EXACT.
   */
  static String gbifType(MatchType type) {
    return switch (type) {
      case EXACT, CANONICAL -> "EXACT";
      case VARIANT -> "VARIANT";
      case HIGHERRANK -> "HIGHERRANK";
      case AMBIGUOUS, NONE, UNSUPPORTED -> "NONE";
    };
  }

  private static MatchType labelType(@Nullable SimpleName query, SimpleName usage) {
    return query == null ? MatchType.EXACT : UsageMatcher.labelType(query, usage);
  }

  static String gbifStatus(@Nullable TaxonomicStatus status) {
    if (status == null) return null;
    return switch (status) {
      case ACCEPTED -> "ACCEPTED";
      case PROVISIONALLY_ACCEPTED, BARE_NAME -> "DOUBTFUL";
      case SYNONYM -> "SYNONYM";
      case AMBIGUOUS_SYNONYM -> "PROPARTE_SYNONYM";
      case MISAPPLIED -> "MISAPPLIED";
    };
  }

  /**
   * Sets usage, acceptedUsage, synonym and the root first classification.
   * A CLB classification starts with the direct parent, which for a synonym is its accepted name.
   * GBIF lists the classification root first and ends it with the accepted usage itself.
   */
  private static void setUsage(GbifNameUsageMatch gm, SimpleNameClassified<SimpleNameCached> u) {
    gm.usage = usage(u);
    List<SimpleNameCached> cl = u.getClassification() == null ? List.of() : u.getClassification();
    gm.synonym = u.getStatus() != null && u.getStatus().isSynonym();
    gm.classification = new ArrayList<>();
    for (var p : cl.reversed()) {
      gm.classification.add(rankedName(p));
    }
    if (gm.synonym) {
      if (!cl.isEmpty()) {
        gm.acceptedUsage = usage(cl.getFirst());
      }
    } else {
      gm.classification.add(rankedName(u));
    }
  }

  private static GbifNameUsageMatch.RankedName rankedName(SimpleName sn) {
    return new GbifNameUsageMatch.RankedName(sn.getId(), sn.getName(), sn.getRank() == null ? null : sn.getRank().name());
  }

  private static GbifNameUsageMatch.Usage usage(SimpleName sn) {
    var u = new GbifNameUsageMatch.Usage();
    u.key = sn.getId();
    u.name = sn.getLabel();
    u.canonicalName = sn.getName();
    u.authorship = sn.getAuthorship();
    u.rank = sn.getRank() == null ? null : sn.getRank().name();
    u.code = sn.getCode() == null ? null : sn.getCode().name();
    u.status = gbifStatus(sn.getStatus());
    u.formattedName = sn.getLabelHtml();
    return u;
  }
}
