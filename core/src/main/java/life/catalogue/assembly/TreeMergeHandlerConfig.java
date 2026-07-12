package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.release.XReleaseConfig;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeMergeHandlerConfig {
  private static final Logger LOG = LoggerFactory.getLogger(TreeMergeHandlerConfig.class);
  private final  SqlSessionFactory factory;
  public final XReleaseConfig xCfg;
  public final @Nullable Taxon incertae;
  public final int datasetKey;
  public final int user;
  private final Set<String> blockedNames = new HashSet<>();
  private final List<Pattern> blockedNamePatterns = new ArrayList<>();
  // resolved usage ids of the protected group root taxa in the sync target dataset
  private final Set<String> protectedUsageIds = new HashSet<>();

  public TreeMergeHandlerConfig(SqlSessionFactory factory, XReleaseConfig rcfg, int datasetKey, int user) {
    this.factory = factory;
    this.xCfg = rcfg == null ? new XReleaseConfig() : rcfg;
    this.datasetKey = datasetKey;
    this.user = user;
    incertae = createIncertaeSedisRoot();
    // upper case blocked names
    if (xCfg.blockedNames != null) {
      for (var bn : xCfg.blockedNames) {
        blockedNames.add(norm(bn));
      }
    }
    if (xCfg.blockedNamePatterns != null) {
      for (var bnp : xCfg.blockedNamePatterns) {
        if (!StringUtils.isBlank(bnp)){
          try {
            var p = Pattern.compile(bnp.trim(), Pattern.CASE_INSENSITIVE);
            blockedNamePatterns.add(p);
          } catch (IllegalArgumentException e) {
            LOG.warn("Invalid name pattern: " + bnp, e);
          }
        }
      }
    }
    resolveProtectedGroups();
  }

  /**
   * Resolves the configured protected groups to their accepted usage in the sync target dataset.
   * Each protected taxon must exist, be accepted and unique - the release fails otherwise.
   * The resolved root ids are used to shield the entire subtree of each group from any merge changes.
   * A warning is logged for every protected group.
   */
  private void resolveProtectedGroups() {
    if (xCfg.protectedGroups == null || xCfg.protectedGroups.isEmpty()) {
      return;
    }
    try (SqlSession session = factory.openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      for (var entry : xCfg.protectedGroups.entrySet()) {
        final Rank rank = entry.getKey();
        if (rank == null || entry.getValue() == null) {
          throw new IllegalArgumentException("Protected group requires a rank and at least one scientific name, but got: " + rank + " / " + entry.getValue());
        }
        for (String name : entry.getValue()) {
          if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Protected group of rank " + rank + " contains a blank scientific name");
          }
          // find all usages by canonical name & rank, regardless of status, then keep only accepted taxa
          SimpleName taxon = null;
          int taxonCount = 0;
          for (var sn : num.findSimple(datasetKey, null, null, rank, name.trim())) {
            if (sn.getStatus() != null && sn.getStatus().isTaxon()) {
              taxon = sn;
              taxonCount++;
            }
          }
          if (taxonCount == 0) {
            throw new IllegalArgumentException("Protected group " + rank + " " + name + " does not exist as an accepted taxon in dataset " + datasetKey);
          }
          if (taxonCount > 1) {
            throw new IllegalArgumentException("Protected group " + rank + " " + name + " is not unique. Found " + taxonCount + " accepted taxa in dataset " + datasetKey);
          }
          protectedUsageIds.add(taxon.getId());
          LOG.warn("Protect {} {} [{}] and its entire subtree from any merge sync updates or inserts", rank, name, taxon.getId());
        }
      }
    }
  }

  public boolean hasProtectedGroups() {
    return !protectedUsageIds.isEmpty();
  }

  /**
   * @return true if the given usage id is the root of a protected group
   */
  public boolean isProtectedRoot(String usageId) {
    return protectedUsageIds.contains(usageId);
  }

  private static String norm(String x) {
    return x == null ? null : x.trim().toUpperCase();
  }

  private @Nullable Taxon createIncertaeSedisRoot() {
    // cached taxon existing? The same config will be reused many times in an XRelease
    if (xCfg.incertaeSedis != null) {
      String pID = null;
      if (xCfg.incertaeSedis.getClassification() != null) {
        var parents = new ArrayList<>(xCfg.incertaeSedis.getClassification());
        Collections.reverse(parents);
        for (var sn : parents) {
          Taxon p = lookupOrCreateTaxon(sn, pID, true);
          pID = p.getId();
        }
      }
      return lookupOrCreateTaxon(xCfg.incertaeSedis, pID, false);
    }
    return null;
  }

  private Taxon lookupOrCreateTaxon(SimpleName sn, String parentID, boolean classification) {
    // lookup existing name
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      var existing = session.getMapper(NameUsageMapper.class).findSimpleSN(datasetKey, sn);
      if (!existing.isEmpty()) {
        var ex = existing.get(0);
        LOG.info("Use existing taxon {} for incertae sedis {}", ex, classification ? "classificaton" : "taxon");
        return tm.get(ex.toDSID(datasetKey));

      } else {
        LOG.info("Create new taxon {} for incertae sedis {}", sn, classification ? "classificaton" : "taxon");
        Name n = new Name(sn);
        n.setDatasetKey(datasetKey);
        n.setId(UUID.randomUUID().toString());
        n.setOrigin(Origin.OTHER);
        n.setType(NameType.PLACEHOLDER);
        n.applyUser(user);

        Taxon t = new Taxon(n);
        t.setDatasetKey(datasetKey);
        // only create new id if not configured
        t.setId(ObjectUtils.coalesce(sn.getId(), UUID.randomUUID().toString()));
        t.setParentId(parentID);
        t.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
        t.setOrigin(Origin.OTHER);
        t.applyUser(user);

        session.getMapper(NameMapper.class).create(n);
        tm.create(t);
        return t;
      }
    }
  }

  /**
   * Tests configured entire names to be excluded.
   * The full scientificName with authorship as well as just the canonical name without authors is queried during backbone builds.
   * @param n name to test for. Case insensitive!
   */
  public boolean isBlocked(FormattableName n) {
    var blocked = blockedNames.contains(norm(n.getLabel()))
           || blockedNames.contains(norm(n.getScientificName()));
    if (!blocked && !blockedNamePatterns.isEmpty()) {
      for (var p : blockedNamePatterns) {
        var m = p.matcher(n.getLabel());
        if (m.find()) {
          return true;
        }
      }
    }
    return blocked;
  }

  public boolean hasIncertae() {
    return incertae != null;
  }

  public static void main(String[] args){
    try {
      var p = Pattern.compile("bnp.trim()", Pattern.CASE_INSENSITIVE);
      p = Pattern.compile("bnp(gh+$$", Pattern.CASE_INSENSITIVE);
    } catch (IllegalArgumentException e) {
      System.out.println(e);
    }

  }
}
