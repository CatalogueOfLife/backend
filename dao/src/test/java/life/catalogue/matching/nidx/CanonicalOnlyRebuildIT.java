package life.catalogue.matching.nidx;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TxtTreeDataRule;
import life.catalogue.matching.RematchJob;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.UnicodeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import it.unimi.dsi.fastutil.ints.IntSet;

import static org.junit.Assert.*;

/**
 * Integration test proving that a full names index rebuild - rematching every dataset with data plus
 * any archived name usages of managed projects, from scratch, through the very same DatasetMatcher /
 * RematchJob path {@code NamesIndexCmd} uses - yields a strictly single-tier, canonical-only names
 * index (see Tasks 1-3 of the names-index refactor):
 * <ul>
 *   <li>every {@code names_index} row is its own canonical, i.e. {@code canonical_id = id}</li>
 *   <li>there is exactly one row per distinct canonical name (no duplicate canonical entries)</li>
 *   <li>every {@code name_match}/{@code name_usage_archive_match} actually produced by the rematch
 *       points at such a canonical row</li>
 * </ul>
 *
 * Reuses the small, deliberately overlapping-name, multi-dataset fixture (trees/nidx1-3.tree) that
 * {@link life.catalogue.matching.NameIndexImplIT#delete()} already relies on to exercise names-index
 * deduplication across datasets - three datasets all containing spelling/rank/authorship variants of
 * "Abbella/Paracentrobia zabinskii", which is exactly the kind of duplication a canonical-only rebuild
 * must collapse onto single entries.
 */
public class CanonicalOnlyRebuildIT {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;

  private static List<TxtTreeDataRule.TreeDataset> fixtureDatasets() {
    List<TxtTreeDataRule.TreeDataset> data = new ArrayList<>();
    data.add(new TxtTreeDataRule.TreeDataset(101, "trees/nidx1.tree"));
    data.add(new TxtTreeDataRule.TreeDataset(102, "trees/nidx2.tree"));
    data.add(new TxtTreeDataRule.TreeDataset(103, "trees/nidx3.tree"));
    return data;
  }

  // fresh, empty postgres schema (no TestDataRule!) loaded with the 3 tree datasets above -
  // the names_index & name_match tables start out genuinely empty, so the rematch below is a real
  // rebuild-from-scratch, not a patch on top of some pre-seeded (and possibly stale two-tier) fixture.
  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(new PgSetupRule())
    .around(new TxtTreeDataRule(fixtureDatasets()));

  /**
   * Recomputes the exact ASCII lookup key {@code NameIndexImpl} uses to bucket names by their
   * canonical form (see {@code NameIndexImpl.key(FormattableName)}), so the test can independently
   * verify - from outside the production class - that the rebuilt index never stores two rows for
   * the same canonical bucket.
   */
  private static String canonicalBucketKey(IndexName n) {
    String origName = NameFormatter.canonicalName(n);
    return UnicodeUtils.replaceNonAscii(SciNameNormalizer.normalize(UnicodeUtils.decompose(origName)).toLowerCase(), '*');
  }

  @Test
  public void rebuildIsCanonicalOnly() throws Exception {
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();

    NameIndex ni = NameIndexFactory.build(NamesIndexConfig.memory(512), factory, aNormalizer).started();
    assertEquals("names index must start empty for a genuine rebuild-from-scratch", 0, ni.size());
    try {
      // full match of every dataset with data, plus archived usages of any managed projects -
      // mirrors NameIndexImplIT.rematchAll() and the DatasetMatcher/RematchJob path NamesIndexCmd uses.
      IntSet keys;
      try (SqlSession session = factory.openSession(true)) {
        keys = DaoUtils.listDatasetWithNames(session);
        keys.addAll(session.getMapper(ArchivedNameUsageMapper.class).listProjects());
      }
      assertFalse("expected the txt tree fixture datasets to have names", keys.isEmpty());
      System.out.println("Full rematch of datasets " + keys + " against a fresh names index");

      RematchJob.some(Users.MATCHER, factory, ni, null, false, keys.toIntArray()).run();
      assertTrue("rematch should have populated the names index", ni.size() > 0);
      System.out.println("Rebuilt names index now holds " + ni.size() + " canonical entries");

      // load every names_index row exactly as it now stands in postgres after the rebuild
      Map<Integer, IndexName> byId = new HashMap<>();
      Set<String> canonicalBucketKeys = new HashSet<>();
      try (SqlSession session = factory.openSession(true)) {
        PgUtils.consume(() -> session.getMapper(NamesIndexMapper.class).processAll(), n -> {
          byId.put(n.getKey(), n);

          // invariant 1: every row is its own canonical - single-tier, canonical-only. Being its own
          // canonical is now structural (no canonical_id column exists to violate it); we still assert
          // the canonical shape - UNRANKED, no authorship - that every row must carry.
          assertTrue("names_index #" + n.getKey() + " is not flagged canonical", n.isCanonical());
          assertEquals("names_index #" + n.getKey() + " must be UNRANKED", Rank.UNRANKED, n.getRank());
          assertNull("names_index #" + n.getKey() + " must carry no authorship", n.getAuthorship());

          // collected for invariant 2 below: no two rows may share the same canonical bucket key
          assertTrue("duplicate canonical names_index entry for " + n.getLabel(), canonicalBucketKeys.add(canonicalBucketKey(n)));
        });
      }
      assertFalse("expected the rebuild to have created names_index entries", byId.isEmpty());

      // invariant 2: exactly one row per distinct canonical name - i.e. no duplicate canonical entries
      assertEquals("names_index size must equal the number of distinct canonical names",
        canonicalBucketKeys.size(), byId.size()
      );

      // invariant 3: every name_match / archived name usage match actually produced by the rebuild
      // references one of those canonical names_index rows
      int matchesChecked = 0;
      try (SqlSession session = factory.openSession(true)) {
        NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
        ArchivedNameUsageMatchMapper anmm = session.getMapper(ArchivedNameUsageMatchMapper.class);
        for (int key : keys.toIntArray()) {
          for (List<Integer> idxIds : List.of(PgUtils.toList(nmm.processIndexIds(key, null)), PgUtils.toList(anmm.processIndexIds(key)))) {
            for (Integer idxId : idxIds) {
              IndexName n = byId.get(idxId);
              // every match references a names_index row that exists - being canonical is now structural
              assertNotNull("a match in dataset " + key + " references missing names_index #" + idxId, n);
              matchesChecked++;
            }
          }
        }
      }
      assertTrue("expected at least one name match to verify", matchesChecked > 0);
      System.out.println("Verified " + byId.size() + " canonical-only names_index rows and " + matchesChecked + " matches referencing them");

    } finally {
      ni.close();
    }
  }
}
