package life.catalogue.interpreter;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.common.kryo.AreaSerializer;
import life.catalogue.dao.TxtTreeDao;
import life.catalogue.parser.*;

import org.checkerframework.checker.units.qual.A;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.SimpleTreeNode;

import java.net.URI;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static life.catalogue.api.vocab.terms.TxtTreeTerm.*;
import static life.catalogue.interpreter.InterpreterUtils.normGeoTime;
import static life.catalogue.parser.SafeParser.parse;

public class TxtTreeInterpreter implements TxtTreeDao.TxTreeNodeInterpreter {
  private static final Pattern VERNACULAR = Pattern.compile("([a-z]{2,3}):(.+)");
  private static final Pattern DISTRIBUTION = Pattern.compile("^([a-z]{3,9}:[^:\\s,]+)(?::([a-z]+))?$"); // LONGHURST = max 9 length

  @Override
  public TxtTreeDao.TxtUsage interpret(SimpleTreeNode tn, boolean synonym, int ordinal, NomCode parentCode, Predicate<String> referenceExists) throws InterruptedException {
    final TxtTreeDao.TxtUsage u = new TxtTreeDao.TxtUsage();

    // CODE is inherited from above
    final NomCode code = SafeParser.parse(NomCodeParser.PARSER, rmSingleDataItem(CODE, tn)).orElse(parentCode, Issue.NOMENCLATURAL_CODE_INVALID, u.issues);

    // RANK
    Rank rank = Rank.UNRANKED; // default for unknown
    try {
      var parsedRank = RankParser.PARSER.parse(code, tn.rank);
      if (parsedRank.isPresent()) {
        rank = parsedRank.get();
      }
    } catch (UnparsableException e) {
      u.issues.addIssue(Issue.RANK_INVALID);
      rank = Rank.OTHER;
    }

    // NAME
    ParsedNameUsage pnu = NameParser.PARSER.parse(tn.name, rank, code, u.issues).get();
    pnu.getName().setId(String.valueOf(tn.id));
    // PUB REF
    if (hasDataItem(PUB, tn)) {
      String[] vals = rmDataItem(PUB, tn);
      for (String val : vals) {
        if (!referenceExists.test(val)) {
          u.issues.addIssue(Issue.REFERENCE_ID_INVALID);
        } else if (pnu.getName().getPublishedInId() != null){
          u.issues.addIssue(Issue.MULTIPLE_PUBLISHED_IN_REFERENCES);
        } else {
          pnu.getName().setPublishedInId(val);
        }
      }
    }

    // general usage props to be removed before we add properties!
    String uid = rmSingleDataItem(ID, tn);
    URI link = parse(UriParser.PARSER, rmSingleDataItem(LINK, tn)).orNull(Issue.URL_INVALID, u.issues);

    // USAGE
    if(synonym) {
      u.usage = new Synonym(pnu.getName());
      u.usage.setOrigin(Origin.SOURCE);
      u.usage.setStatus(TaxonomicStatus.SYNONYM);
    } else {
      TaxonomicStatus status = rmBoolean(PROV, tn, u.issues) || tn.provisional || pnu.isDoubtful() ? TaxonomicStatus.PROVISIONALLY_ACCEPTED : TaxonomicStatus.ACCEPTED;
      var t = new Taxon(pnu.getName());
      u.usage = t;
      t.setStatus(status);
      t.setOrdinal(ordinal);
      // DAGGER
      t.setExtinct(tn.extinct);
      // ENVIRONMENT
      if (hasDataItem(ENV, tn)) {
        String[] vals = rmDataItem(ENV, tn);
        for (String val : vals) {
          Environment env = parse(EnvironmentParser.PARSER, val).orNull(Issue.ENVIRONMENT_INVALID, u.issues);
          if (env != null) {
            t.getEnvironments().add(env);
          }
        }
      }
      // CHRONO TEMPORAL RANGE
      if (hasDataItem(CHRONO, tn)) {
        String[] vals = rmDataItem(CHRONO, tn);
        for (String val : vals) {
          var range = val.split("-");
          if (range.length==1) {
            t.setTemporalRangeStart(normGeoTime(range[0], u.issues));
            t.setTemporalRangeEnd(normGeoTime(range[0], u.issues));
          } else if (range.length==2) {
            t.setTemporalRangeStart(normGeoTime(range[0], u.issues));
            t.setTemporalRangeEnd(normGeoTime(range[1], u.issues));
          } else {
            u.issues.addIssue(Issue.GEOTIME_INVALID);
          }
        }
      }
      // TAX REF
      if (hasDataItem(REF, tn)) {
        String[] vals = rmDataItem(REF, tn);
        for (String val : vals) {
          if (referenceExists.test(val)) {
            t.addReferenceId(val);
          } else {
            u.issues.addIssue(Issue.REFERENCE_ID_INVALID);
          }
        }
      }
      // Vernacular
      if (hasDataItem(VERN, tn)) {
        String[] vals = rmDataItem(VERN, tn);
        for (String val : vals) {
          var m = VERNACULAR.matcher(val);
          if (m.find()) {
            VernacularName vn = new VernacularName();
            var lang = parse(LanguageParser.PARSER, m.group(1)).orNull(Issue.VERNACULAR_LANGUAGE_INVALID, u.issues);
            vn.setLanguage(lang);
            vn.setName(m.group(2));
            u.vernacularNames.add(vn);
          } else {
            u.issues.addIssue(Issue.VERNACULAR_NAME_INVALID);
          }
        }
      }
      // Distribution - iso:de:native
      if (hasDataItem(DIST, tn)) {
        String[] vals = rmDataItem(DIST, tn);
        for (String val : vals) {
          var m = DISTRIBUTION.matcher(val);
          if (m.find()) {
            var d = new Distribution();
            var area = SafeParser.parse(AreaParser.PARSER, m.group(1)).orNull(Issue.DISTRIBUTION_AREA_INVALID, u.issues);
            if (area != null) {
              d.setArea(area);
              var dstat = SafeParser.parse(DistributionStatusParser.PARSER, m.group(2)).orNull(Issue.DISTRIBUTION_STATUS_INVALID, u.issues);
              d.setStatus(dstat);
              u.distributions.add(d);
            } else {
              u.issues.addIssue(Issue.DISTRIBUTION_INVALID);
            }
          } else {
            u.issues.addIssue(Issue.DISTRIBUTION_INVALID);
          }
        }
      }

      // ALL OTHER
      for (var entry : tn.infos.entrySet()) {
        // ignore the PUB entry which we handle below
        if (!entry.getKey().equalsIgnoreCase(PUB.name())) {
          var tp = new TaxonProperty();
          if (entry.getValue() == null || entry.getValue().length == 0) continue;
          tp.setProperty(entry.getKey().toLowerCase());
          tp.setValue(String.join(", ", entry.getValue()));
          u.properties.add(tp);
        }
      }
    }
    u.usage.setOrigin(Origin.SOURCE);
    u.usage.setId(ObjectUtils.coalesce(uid, String.valueOf(tn.id)));
    u.usage.setLink(link);
    u.usage.setAccordingToId(pnu.getTaxonomicNote());
    // COMMENT
    u.usage.setRemarks(tn.comment);
    return u;
  }

  private static boolean hasDataItem(TxtTreeTerm key, SimpleTreeNode tn) {
    return tn.infos != null && tn.infos.containsKey(key.name());
  }
  private static String[] rmDataItem(TxtTreeTerm key, SimpleTreeNode tn) {
    return tn.infos.remove(key.name());
  }
  private static String rmSingleDataItem(TxtTreeTerm key, SimpleTreeNode tn) {
    var vals = tn.infos.remove(key.name());
    if (vals == null || vals.length == 0) {
      return null;
    } else if (vals.length == 1) {
      return vals[0];
    } else {
      return String.join(",", vals);
    }
  }
  private static boolean rmBoolean(TxtTreeTerm key, SimpleTreeNode tn, IssueContainer issues) {
    var val = rmSingleDataItem(key, tn);
    return SafeParser.parse(BooleanParser.PARSER, val).orElse(Boolean.FALSE, Issue.PROVISIONAL_STATUS_INVALID, issues);
  }
}
