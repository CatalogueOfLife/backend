package life.catalogue.assembly;

import life.catalogue.api.model.FormattableName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.release.XReleaseConfig;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NameType;

import java.util.*;
import java.util.regex.Pattern;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeMergeHandlerConfig {
  private static final Logger LOG = LoggerFactory.getLogger(TreeMergeHandlerConfig.class);
  private final  SqlSessionFactory factory;
  public final XReleaseConfig xCfg;
  public final Taxon incertae;
  public final int datasetKey;
  public final int user;
  private final Set<String> blockedNames = new HashSet<>();
  private final List<Pattern> blockedNamePatterns = new ArrayList<>();

  public TreeMergeHandlerConfig(SqlSessionFactory factory, XReleaseConfig xcfg, int datasetKey, int user) {
    this.factory = factory;
    this.xCfg = xcfg;
    this.datasetKey = datasetKey;
    this.user = user;
    incertae = createIncertaeSedisRoot();
    // upper case blocked names
    if (xcfg.blockedNames != null) {
      for (var bn : xcfg.blockedNames) {
        blockedNames.add(bn.trim().toUpperCase());
      }
    }
    if (xcfg.blockedNamePatterns != null) {
      for (var bnp : xcfg.blockedNamePatterns) {
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
  }

  private Taxon createIncertaeSedisRoot() {
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
        t.setId(UUID.randomUUID().toString());
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
    var blocked = blockedNames.contains(n.getLabel().trim().toUpperCase())
           || blockedNames.contains(n.getScientificName().trim().toUpperCase());
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
