package org.col.importer;

import java.net.URI;
import java.util.Set;

import com.google.common.collect.Sets;
import org.col.importer.neo.model.Labels;
import org.col.importer.neo.model.NeoName;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.neo.model.RankedUsage;
import org.col.importer.neo.traverse.Traversals;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.Iterators;

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
      assertEquals(0, acc.descriptions.size());
      assertEquals(0, acc.media.size());
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
      assertEquals("Latrodectus tredecimguttatus", t.usage.getName().getScientificName());
      assertEquals("(Rossi, 1790)", t.usage.getName().authorshipComplete());

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
      assertEquals("Lutzomyia preclara", t.usage.getName().getScientificName());

      t = usageByID("2");
      assertEquals("Latrodectus", t.usage.getName().getScientificName());
      assertEquals("(Fabricius, 1775)", t.usage.getName().authorshipComplete().trim());
      assertEquals(Rank.GENUS, t.usage.getName().getRank());

      t = usageByID("3");
      assertEquals("Null bactus", t.usage.getName().getScientificName());
      assertTrue(store.getVerbatim(t.usage.getName().getVerbatimKey()).hasIssue(Issue.NULL_EPITHET));
    }
  }
  
  @Test
  public void acef8Nons() throws Exception {
    normalize(8);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u;
      for (Node n : Iterators.loop(store.getNeo().findNodes(Labels.USAGE))) {
        u = store.usageWithName(n);
        if (u.usage.getName().getOrigin() == Origin.SOURCE) {
          System.out.println(u.usage.getStatus() + ": " + u.usage.getName().canonicalNameComplete());
          System.out.println("  " + u.usage.getName().getRemarks());
          System.out.println("  " + u.usage.getAccordingTo());
          assertNotNull(u.usage.getAccordingTo());
        }
      }
      
      u = usageByID("8");
      assertEquals("Anthurium lanceum", u.usage.getName().getScientificName());
      assertEquals("Engl.", u.usage.getName().authorshipComplete());
      assertEquals("nom.illeg.; superfluous at its time of publication", u.usage.getName().getRemarks());
      assertEquals("Markus non. A.lancea.", u.usage.getAccordingTo());
      assertEquals(NomStatus.UNACCEPTABLE, u.usage.getName().getNomStatus());
  
      u = usageByID("11");
      assertEquals("Abies alba", u.usage.getName().getScientificName());
      assertEquals("Mill.", u.usage.getName().authorshipComplete());
      assertEquals("valid", u.usage.getName().getRemarks());
      assertEquals("non Parolly", u.usage.getAccordingTo());
      assertEquals(NomStatus.ACCEPTABLE, u.usage.getName().getNomStatus());
    }
  }
  
  @Test
  public void acefNameIssues() throws Exception {
    normalize(9);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = usageByID("Lamprostiba_pu!chra");
      assertEquals("Lamprostiba pu!chra", u.usage.getName().getScientificName());
      assertEquals("Pace, 2014", u.usage.getName().authorshipComplete());
      VerbatimRecord v = verbatim(u.usage.getName());
      assertTrue(v.hasIssue(Issue.UNUSUAL_NAME_CHARACTERS));
      assertTrue(v.hasIssue(Issue.PARTIALLY_PARSABLE_NAME));
      assertTrue(v.hasIssue(Issue.PARSED_NAME_DIFFERS));
  
      u = usageByID("Eusphalerum_caucasicum_feldmanni");
      assertNull(u);
      
      v = vByLine(AcefTerm.AcceptedInfraSpecificTaxa, 2);
      assertTrue(v.hasIssue(Issue.PARENT_ID_INVALID));
    }
  }
  
  @Test
  public void acefInfrspecies() throws Exception {
    normalize(10);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = usageByID("Scr-13-.01-.01-.00-.001-.001-.014-.b");
      assertEquals("Odontotrypes farkaci habaensis", u.usage.getName().getScientificName());
      assertEquals("Ochi, Kon & Bai, 2018", u.usage.getName().authorshipComplete());
      assertEquals(Rank.INFRASPECIFIC_NAME, u.usage.getName().getRank());

      // this is a duplicate ID and we expect the subspecies to be dropped in favor of the species
      u = usageByID("Scr-04-.01-.01-.00-.002-.000-.009-.b");
      assertEquals("Aegialia conferta", u.usage.getName().getScientificName());
      assertEquals("Brown, 1931", u.usage.getName().authorshipComplete());
      assertEquals(Rank.SPECIES, u.usage.getName().getRank());

      VerbatimRecord v = verbatim(u.usage.getName());
      assertTrue(v.hasIssue(Issue.ID_NOT_UNIQUE));
      assertFalse(v.hasIssue(Issue.NOT_INTERPRETED));
  
  
      int counter = 0;
      for (VerbatimRecord vr : store.verbatimList()) {
        counter++;
        if (vr.getType() == AcefTerm.AcceptedInfraSpecificTaxa) {
          if (vr.get(AcefTerm.AcceptedTaxonID).equals("Scr-04-.01-.01-.00-.002-.000-.009-.b")) {
            // this is the duplicate
            assertEquals("nigrella", vr.get(AcefTerm.InfraSpeciesEpithet));
            assertTrue(vr.hasIssue(Issue.NOT_INTERPRETED));
            assertTrue(vr.hasIssue(Issue.ID_NOT_UNIQUE));
  
          } else {
            assertEquals("habaensis", vr.get(AcefTerm.InfraSpeciesEpithet));
          }
        }
      }
      assertEquals(5, counter);
    }
  }
  
  private VerbatimRecord verbatim(VerbatimEntity obj) {
    return store.getVerbatim(obj.getVerbatimKey());
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
  
  @Test
  public void acefGenusOnly() throws Exception {
    normalize(15);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("a");
      assertEquals("Dictis", t.usage.getName().getScientificName());
      assertEquals("L.Koch, 1872", t.usage.getName().getAuthorship());
      assertEquals(Rank.GENUS, t.usage.getName().getRank());
  
      t = usageByID("ia");
      assertNull(t);
  
      t = usageByID("ib");
      assertEquals("Scyloxes asiatica carambula", t.usage.getName().getScientificName());
      assertEquals("Dunin, 2001", t.usage.getName().getAuthorship());
      assertEquals(Rank.INFRASPECIFIC_NAME, t.usage.getName().getRank());
    }
  }
  
  /**
   * Makes sure placeholder names in the denormed ACEF classification get flagged
   */
  @Test
  public void acefNotAssigned() throws Exception {
    normalize(16);
    try (Transaction tx = store.getNeo().beginTx()) {
      assertPlaceholderInParents("411000");
      assertPlaceholderInParents("410933");
      assertPlaceholderInParents("291295");
    }
  }
  
  void assertPlaceholderInParents(String id) {
    NeoUsage t = usageByID(id);
    for (RankedUsage u : store.parents(t.node)) {
      NeoName n = store.names().objByNode(u.nameNode);
      if (NameType.PLACEHOLDER == n.name.getType()) {
        return;
      }
    }
    fail("No placeholder name found in parents of " + id);
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
