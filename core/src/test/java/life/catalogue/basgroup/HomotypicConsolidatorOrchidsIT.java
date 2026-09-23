package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.*;

import java.util.HashSet;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Orchid epithets published by the same author in several genera.
 * https://github.com/CatalogueOfLife/backend/issues/1600
 */
public class HomotypicConsolidatorOrchidsIT {
  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final int datasetKey = 100;
  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(datasetKey, "txtree/homconsolidation-orchids.txtree")) // loads prio values into sector keys - lower is better
    .around(matchingRule);

  @Test
  public void sameAuthorSeveralGenera() throws Exception {
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? Integer.MAX_VALUE : lnu.getSectorKey()
    );
    hc.consolidate();
    HomotypicConsolidatorIT.assertNoLoop(datasetKey);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var nm = session.getMapper(NameMapper.class);
      var num = session.getMapper(NameUsageMapper.class);
      Set<String> rels = new HashSet<>();
      for (NameRelation nr : session.getMapper(NameRelationMapper.class).processDataset(datasetKey)) {
        if (nr.getCreatedBy() == Users.HOMOTYPIC_GROUPER) {
          var n1 = nm.get(DSID.of(datasetKey, nr.getNameId()));
          var n2 = nm.get(DSID.of(datasetKey, nr.getRelatedNameId()));
          if (nr.getType() == NomRelType.SPELLING_CORRECTION) {
            assertEquals("spelling correction across genera: " + n1.getLabel() + " -> " + n2.getLabel(), n1.getGenus(), n2.getGenus());
          }
          rels.add(n1.getLabel() + " " + nr.getType() + " " + n2.getLabel());
        }
      }
      rels.forEach(System.out::println);
      // Altensteinia columbiana (Schltr.) Garay is no orthographic variant of the colombiana names by its strict authorship,
      // so it stays alone and only has its synonymy
      assertEquals(Set.of(
        "Neolehmannia mathewsii (Rchb.f.) Garay BASIONYM Epidendrum mathewsii Rchb.f.",
        "Nanodes mathewsii (Rchb.f.) Rolfe BASIONYM Epidendrum mathewsii Rchb.f.",
        "Aa matthewsii (Rchb.f.) Schltr. BASIONYM Altensteinia matthewsii Rchb.f.",
        "Aa mathewsii (Rchb.fil.) Schltr. BASIONYM Altensteinia matthewsii Rchb.f.",
        // the source lists Eria under Phreatia, which makes them one name, one of them missing its brackets
        "Eria matthewsii Rchb.f. HOMOTYPIC Phreatia matthewsii Rchb.f.",
        "Pinalia matthewsii (Rchb.f.) Kuntze BASIONYM Phreatia matthewsii Rchb.f.",
        "Eurystyles colombiana (Schltr.) Schltr. BASIONYM Trachelosiphon colombianum Schltr.",
        "Trachelosiphon colombianum Schltr. SPELLING_CORRECTION Trachelosiphon columbianum Schltr."
      ), rels);

      // the merged duplicate is consolidated into the base name with the real basionym
      var aam2 = num.get(DSID.of(datasetKey, "aam2"));
      assertEquals(TaxonomicStatus.SYNONYM, aam2.getStatus());
      assertEquals("aam", aam2.getParentId());

      // untouched
      for (var id : new String[]{"epi", "phr", "gra", "aam", "oct", "aac", "eur"}) {
        assertEquals(id, TaxonomicStatus.ACCEPTED, num.get(DSID.of(datasetKey, id)).getStatus());
      }
      assertEquals("phr", num.get(DSID.of(datasetKey, "pin")).getParentId());

      // the same name twice from the same source is unresolved: flagged, and no relations as asserted above
      var vm = session.getMapper(VerbatimSourceMapper.class);
      for (var id : new String[]{"mon", "mon2"}) {
        assertEquals(TaxonomicStatus.ACCEPTED, num.get(DSID.of(datasetKey, id)).getStatus());
        var v = vm.getByUsage(DSID.of(datasetKey, id));
        assertNotNull(v);
        assertTrue(v.getIssues().contains(Issue.HOMOTYPIC_CONSOLIDATION_UNRESOLVED));
      }
      var v = vm.getByUsage(DSID.of(datasetKey, "oct"));
      assertTrue(v == null || !v.getIssues().contains(Issue.HOMOTYPIC_CONSOLIDATION_UNRESOLVED));
    }
  }
}
