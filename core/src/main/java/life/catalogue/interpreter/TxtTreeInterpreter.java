package life.catalogue.interpreter;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.dao.TxtTreeDao;
import life.catalogue.parser.*;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.SimpleTreeNode;

import java.net.URI;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static life.catalogue.api.vocab.terms.TxtTreeTerm.*;
import static life.catalogue.interpreter.InterpreterUtils.normGeoTime;
import static life.catalogue.parser.SafeParser.parse;

public class TxtTreeInterpreter implements TxtTreeDao.TxTreeNodeInterpreter {
  private static final Pattern VERNACULAR = Pattern.compile("([a-z]{2,3}):(.+)");
  private static final Pattern DISTRIBUTION = Pattern.compile("^([a-z]{3,9}:[^:\\s,]+)(?::([a-z]+))?$"); // LONGHURST = max 9 length
  private static final Pattern TYPE_MATERIAL = Pattern.compile("([a-z]{5,}):(.+)");

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
      u.issues.add(Issue.RANK_INVALID);
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
          u.issues.add(Issue.REFERENCE_ID_INVALID);
        } else if (pnu.getName().getPublishedInId() != null){
          u.issues.add(Issue.MULTIPLE_PUBLISHED_IN_REFERENCES);
        } else {
          pnu.getName().setPublishedInId(val);
        }
      }
    }
    // NOM STATUS
    if (hasDataItem(NOM, tn)) {
      var nomStatus = parse(NomStatusParser.PARSER, rmSingleDataItem(NOM, tn)).orNull(Issue.NOMENCLATURAL_STATUS_INVALID, u.issues);
      pnu.getName().setNomStatus(nomStatus);
    }

    // general usage props to be removed before we add properties!
    String uid = rmSingleDataItem(ID, tn);
    URI link = parse(UriParser.PARSER, rmSingleDataItem(LINK, tn)).orNull(Issue.URL_INVALID, u.issues);

    // USAGE
    if(synonym) {
      u.usage = new Synonym(pnu.getName());
      u.usage.setOrigin(Origin.SOURCE);
      u.usage.setStatus(TaxonomicStatus.SYNONYM);
      if (hasAndRmDataItem(tn, PROV, ENV, CHRONO, REF, VERN, DIST)) {
        u.issues.add(Issue.SYNONYM_WITH_TAXON_PROPERTY);
      }
      interpretTypeMaterial(tn, u);

    } else {
      TaxonomicStatus status = rmBoolean(PROV, tn, u.issues) || tn.provisional || pnu.isDoubtful() ? TaxonomicStatus.PROVISIONALLY_ACCEPTED : TaxonomicStatus.ACCEPTED;
      var t = new Taxon(pnu.getName());
      u.usage = t;
      t.setStatus(status);
      t.setOrdinal(ordinal);
      interpretTypeMaterial(tn, u);
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
            u.issues.add(Issue.GEOTIME_INVALID);
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
            u.issues.add(Issue.REFERENCE_ID_INVALID);
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
            vn.setName(decode(m.group(2)));
            u.vernacularNames.add(vn);
          } else {
            u.issues.add(Issue.VERNACULAR_NAME_INVALID);
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
              var means = SafeParser.parse(EstablishmentMeansParser.PARSER, m.group(2)).orNull(Issue.DISTRIBUTION_STATUS_INVALID, u.issues);
              d.setEstablishmentMeans(means);
              u.distributions.add(d);
            } else {
              u.issues.add(Issue.DISTRIBUTION_INVALID);
            }
          } else {
            u.issues.add(Issue.DISTRIBUTION_INVALID);
          }
        }
      }
      // Species Estimates - 45000,†340
      if (hasDataItem(EST, tn)) {
        String raw = rmSingleDataItem(EST, tn);
        if (raw != null) {
          String[] vals = raw.split(",");
          for (String val : vals) {
            var type = EstimateType.SPECIES_LIVING;
            if (val.charAt(0) == NameUsageBase.EXTINCT_SYMBOL) {
              type = EstimateType.SPECIES_EXTINCT;
              val = val.substring(1);
            }
            try {
              var num = Integer.parseInt(val.trim());
              var est = new SpeciesEstimate();
              est.setEstimate(num);
              est.setType(type);
              u.estimates.add(est);
            } catch (NumberFormatException e) {
              u.issues.add(Issue.ESTIMATE_INVALID);
            }
          }
        }
      }
      // ALL OTHER as PROPERTIES
      for (var entry : tn.infos.entrySet()) {
        // ignore the PUB entry which we handle below
        if (!entry.getKey().equalsIgnoreCase(PUB.name())) {
          var tp = new TaxonProperty();
          if (entry.getValue() == null || entry.getValue().length == 0) continue;
          tp.setProperty(entry.getKey().toLowerCase());
          tp.setValue(Arrays.stream(entry.getValue())
            .map(TxtTreeInterpreter::decode)
            .collect(Collectors.joining(", "))
          );
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

  private void interpretTypeMaterial(SimpleTreeNode tn, TxtTreeDao.TxtUsage u) {
    // TYPE
    if (hasDataItem(TYPE, tn)) {
      u.type = decode(rmSingleDataItem(TYPE, tn));
    }
    // TYPE MATERIAL
    if (hasDataItem(TM, tn)) {
      String[] vals = rmDataItem(TM, tn);
      for (String val : vals) {
        var tm = new TypeMaterial();
        var m = TYPE_MATERIAL.matcher(val);
        if (m.find()) {
          var status = parse(TypeStatusParser.PARSER, m.group(1)).orNull(Issue.TYPE_STATUS_INVALID, u.issues);
          tm.setStatus(status);
          tm.setCitation(decode(m.group(2)));
        } else {
          tm.setCitation(decode(val));
        }
        u.typeMaterial.add(tm);
      }
    }
  }

  private static String decode(String x) {
    return x == null ? null : x.replaceAll("_", " ");
  }

  private static boolean hasDataItem(TxtTreeTerm key, SimpleTreeNode tn) {
    return tn.infos != null && tn.infos.containsKey(key.name());
  }
  private static boolean hasAndRmDataItem(SimpleTreeNode tn, TxtTreeTerm... keys) {
    boolean has = false;
    for (var key : keys) {
      var val = rmDataItem(key, tn);
      has = has || (val != null && val.length > 0);
    }
    return has;
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
