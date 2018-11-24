package org.col.admin.importer;

import java.net.URI;
import java.util.Set;

import com.google.common.collect.Sets;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.neo.traverse.Traversals;
import org.col.api.model.Dataset;
import org.col.api.model.Distribution;
import org.col.api.model.VernacularName;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.Rank;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerACEFIT extends NormalizerITBase {
  
  public NormalizerACEFIT() {
    super(DataFormat.ACEF);
  }
  
  @Test
  public void acef0() throws Exception {
    normalize(0);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("s7");
      // synonym has no accepted thus gets removed!
      assertNull(t);
      
      NeoName n = nameByID("s7");
      assertEquals("Astragalus nonexistus", n.name.getScientificName());
      assertEquals("DC.", n.name.authorshipComplete());
      assertEquals(Rank.SPECIES, n.name.getRank());

      assertTrue(hasIssues(n, Issue.SYNONYM_DATA_MOVED));
      assertTrue(hasIssues(n, Issue.ACCEPTED_ID_INVALID));

      
      t = usageByID("s6");
      assertTrue(t.isSynonym());
      assertEquals("Astragalus beersabeensis", t.usage.getName().getScientificName());
      assertEquals(Rank.SPECIES, t.usage.getName().getRank());
      assertTrue(hasIssues(t, Issue.SYNONYM_DATA_MOVED));
      assertTrue(t.classification.isEmpty());
      assertEquals(0, t.vernacularNames.size());
      assertEquals(0, t.distributions.size());
      assertEquals(0, t.bibliography.size());
      NeoUsage acc = accepted(t.node);
      assertEquals(1, acc.vernacularNames.size());
      assertEquals(2, acc.distributions.size());
      assertEquals(2, acc.bibliography.size());
      
      VernacularName v = acc.vernacularNames.get(0);
      assertEquals("Beer bean", v.getName());
    }
  }
  
  @Test
  public void acefSample() throws Exception {
    normalize(1);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("14649");
      assertEquals("Zapoteca formosa", t.usage.getName().getScientificName());
      assertEquals("(Kunth) H.M.Hern.", t.usage.getName().authorshipComplete());
      assertEquals(Rank.SPECIES, t.usage.getName().getRank());
      assertEquals("Fabaceae", t.classification.getFamily());
      
      // distributions
      assertEquals(3, t.distributions.size());
      Set<String> areas = Sets.newHashSet("AGE-BA", "BZC-MS", "BZC-MT");
      for (Distribution d : t.distributions) {
        assertEquals(Gazetteer.TDWG, d.getGazetteer());
        assertTrue(areas.remove(d.getArea()));
      }
      
      // vernacular
      assertEquals(3, t.vernacularNames.size());
      Set<String> names = Sets.newHashSet("Ramkurthi", "Ram Kurthi", "отчество");
      for (VernacularName v : t.vernacularNames) {
        assertEquals(v.getName().startsWith("R") ? Language.HINDI : Language.RUSSIAN,
            v.getLanguage());
        assertTrue(names.remove(v.getName()));
      }
      
      // denormed family
      t = byName("Fabaceae", null);
      assertEquals("Fabaceae", t.usage.getName().getScientificName());
      assertEquals("Fabaceae", t.usage.getName().canonicalNameComplete());
      assertNull(t.usage.getName().authorshipComplete());
      assertEquals(Rank.FAMILY, t.usage.getName().getRank());
    }
  }
  
  @Test
  public void acef4NonUnique() throws Exception {
    normalize(4);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1");
      assertEquals("Inga vera", t.usage.getName().getScientificName());
      assertEquals("Willd.", t.usage.getName().authorshipComplete());
      assertEquals(Rank.SPECIES, t.usage.getName().getRank());
      assertTrue(hasIssues(t, Issue.ID_NOT_UNIQUE));
      assertEquals("Fabaceae", t.classification.getFamily());
      assertEquals("Plantae", t.classification.getKingdom());
      
      // vernacular
      assertEquals(2, t.vernacularNames.size());
    }
  }
  
  @Test
  public void acef5Datasource() throws Exception {
    normalize(5);
    
    Dataset d = store.getDataset();
    assertEquals("Systema Dipterorum", d.getTitle());
  }
  
  @Test
  public void acef6Misapplied() throws Exception {
    normalize(6);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("MD1");
      assertEquals("Latrodectus tredecimguttatus (Rossi, 1790)", t.usage.getName().canonicalNameComplete());

      Set<String> nonMisappliedIds = Sets.newHashSet("s17", "s18");
      int counter = 0;
      for (Node sn : Traversals.SYNONYMS.traverse(t.node).nodes()) {
        NeoUsage s = store.usageWithName(sn);
        assertTrue(s.getSynonym().getStatus().isSynonym());
        if (nonMisappliedIds.remove(s.getId())) {
          assertEquals(TaxonomicStatus.SYNONYM, s.getSynonym().getStatus());
          assertFalse(hasIssues(s, Issue.DERIVED_TAXONOMIC_STATUS));
        } else {
          counter++;
          // assertEquals(TaxonomicStatus.MISAPPLIED, s.synonym.getStatus());
          // assertTrue(hasIssues(s, Issue.DERIVED_TAXONOMIC_STATUS));
        }
      }
      assertTrue(nonMisappliedIds.isEmpty());
      assertEquals(6, counter);
    }
  }
  
  @Test
  public void acef7Nulls() throws Exception {
    normalize(7);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("CIP-S-902");
      assertEquals("Lutzomyia preclara", t.usage.getName().canonicalNameComplete());

      t = usageByID("2");
      assertEquals("Latrodectus spec.", t.usage.getName().canonicalNameComplete());
      assertEquals("(Fabricius, 1775)", t.usage.getName().authorshipComplete().trim());

      t = usageByID("3");
      assertEquals("Null bactus", t.usage.getName().canonicalNameComplete());
      assertTrue(store.getVerbatim(t.usage.getName().getVerbatimKey()).hasIssue(Issue.NULL_EPITHET));
    }
  }
  
  /**
   * ICTV GSD with "parsed" virus names https://github.com/Sp2000/colplus-backend/issues/65
   */
  @Test
  public void acef14virus() throws Exception {
    normalize(14);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("Vir-96");
      assertEquals("Phikmvlikevirus: Pseudomonas phage LKA1 ICTV", t.usage.getName().getScientificName());
    }
  }
  
  /**
   * Full Systema Diptera dataset with 170.000 names. Takes 2 minutes, be patient
   */
  @Test
  @Ignore("large dataset that takes a long time")
  public void acef101() throws Exception {
    normalize(101);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("Sys-2476");
      assertNotNull(t);
      assertEquals("Chagasia maculata", t.usage.getName().getScientificName());
    }
  }
  
  @Test
  @Ignore("external dependency")
  public void testGsdGithub() throws Exception {
    normalize(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/19.tar.gz"));
  }
  
}
