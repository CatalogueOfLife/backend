package org.col.admin.importer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NeoDbFactory;
import org.col.admin.importer.neo.NotUniqueRuntimeException;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.neo.model.RankedName;
import org.col.admin.importer.neo.printer.GraphFormat;
import org.col.admin.importer.neo.printer.PrinterUtils;
import org.col.admin.importer.neo.traverse.Traversals;
import org.col.admin.matching.NameIndexFactory;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.Rank;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerACEFIT {

  private NeoDb store;
  private NormalizerConfig cfg;
  private Path acef;

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      store.closeAndDelete();
    }
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }


  /**
   * Normalizes a ACEF folder from the test resources and checks its printed txt tree against the
   * expected tree
   * 
   * @param datasetKey
   */
  private void normalize(int datasetKey) throws Exception {
    URL acefUrl = getClass().getResource("/acef/" + datasetKey);
    normalize(Paths.get(acefUrl.toURI()));
  }

  private void normalize(URI url) throws Exception {
    // download an decompress
    ExternalSourceUtil.consumeSource(url, this::normalize);
  }

  private void normalize(Path source) {
    try {
      acef = source;

      store = NeoDbFactory.create(1, cfg);
      Dataset d = new Dataset();
      d.setKey(1);
      d.setDataFormat(DataFormat.ACEF);
      store.put(d);

      Normalizer norm = new Normalizer(store, acef, NameIndexFactory.passThru());
      norm.call();

      // reopen
      store = NeoDbFactory.open(1, cfg);

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  NeoTaxon byID(String id) {
    Node n = store.byID(id);
    return store.get(n);
  }

  NeoTaxon byName(String name, @Nullable String author) {
    List<Node> nodes = store.byScientificName(name);
    // filter by author
    if (author != null) {
      nodes.removeIf(n -> !author.equalsIgnoreCase(NeoProperties.getAuthorship(n)));
    }

    if (nodes.isEmpty()) {
      throw new NotFoundException();
    }
    if (nodes.size() > 1) {
      throw new NotUniqueRuntimeException("scientificName", name);
    }
    return store.get(nodes.get(0));
  }

  NeoTaxon accepted(Node syn) {
    List<RankedName> accepted = store.accepted(syn);
    if (accepted.size() != 1) {
      throw new IllegalStateException("Synonym has " + accepted.size() + " accepted taxa");
    }
    return store.get(accepted.get(0).node);
  }

  private boolean hasIssues(VerbatimEntity ent, Issue... issues) {
    IssueContainer ic = store.getVerbatim(ent.getVerbatimKey());
    for (Issue is : issues) {
      if (!ic.hasIssue(is))
        return false;
    }
    return true;
  }

  @Test
  public void acef0() throws Exception {
    normalize(0);
    writeToFile();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("s7");
      assertTrue(t.isSynonym());
      assertEquals("Astragalus nonexistus", t.name.getScientificName());
      assertEquals("DC.", t.name.authorshipComplete());
      assertEquals(Rank.SPECIES, t.name.getRank());

      assertTrue(hasIssues(t, Issue.SYNONYM_DATA_MOVED));
      assertTrue(hasIssues(t, Issue.ACCEPTED_ID_INVALID));
      assertTrue(t.classification.isEmpty());
      assertEquals(0, t.vernacularNames.size());
      assertEquals(0, t.distributions.size());
      assertEquals(0, t.bibliography.size());
      // missing accepted
      assertEquals(0, store.accepted(t.node).size());

      t = byID("s6");
      assertTrue(t.isSynonym());
      assertEquals("Astragalus beersabeensis", t.name.getScientificName());
      assertEquals(Rank.SPECIES, t.name.getRank());
      assertTrue(hasIssues(t, Issue.SYNONYM_DATA_MOVED));
      assertTrue(t.classification.isEmpty());
      assertEquals(0, t.vernacularNames.size());
      assertEquals(0, t.distributions.size());
      assertEquals(0, t.bibliography.size());
      NeoTaxon acc = accepted(t.node);
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
      NeoTaxon t = byID("14649");
      assertEquals("Zapoteca formosa", t.name.getScientificName());
      assertEquals("(Kunth) H.M.Hern.", t.name.authorshipComplete());
      assertEquals(Rank.SPECIES, t.name.getRank());
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
      assertEquals("Fabaceae", t.name.getScientificName());
      assertEquals("Fabaceae", t.name.canonicalNameComplete());
      assertNull(t.name.authorshipComplete());
      assertEquals(Rank.FAMILY, t.name.getRank());
    }
  }

  @Test
  public void acef4NonUnique() throws Exception {
    normalize(4);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("1");
      assertEquals("Inga vera", t.name.getScientificName());
      assertEquals("Willd.", t.name.authorshipComplete());
      assertEquals(Rank.SPECIES, t.name.getRank());
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
      NeoTaxon t = byID("MD1");
      assertEquals("Latrodectus tredecimguttatus (Rossi, 1790)", t.name.canonicalNameComplete());

      Set<String> nonMisappliedIds = Sets.newHashSet("s17", "s18");
      int counter = 0;
      for (Node sn : Traversals.SYNONYMS.traverse(t.node).nodes()) {
        NeoTaxon s = store.get(sn);
        assertTrue(s.synonym.getStatus().isSynonym());
        if (nonMisappliedIds.remove(s.getID())) {
          assertEquals(TaxonomicStatus.SYNONYM, s.synonym.getStatus());
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
      NeoTaxon t = byID("CIP-S-902");
      assertEquals("Lutzomyia preclara", t.name.canonicalNameComplete());

      t = byID("2");
      assertEquals("Latrodectus spec.", t.name.canonicalNameComplete());
      assertEquals("(Fabricius, 1775)", t.name.authorshipComplete().trim());

      t = byID("3");
      assertEquals("Null bactus", t.name.canonicalNameComplete());
      assertTrue(store.getVerbatim(t.name.getVerbatimKey()).hasIssue(Issue.NULL_EPITHET));
    }
  }

  /**
   * ICTV GSD with "parsed" virus names https://github.com/Sp2000/colplus-backend/issues/65
   */
  @Test
  public void acef14virus() throws Exception {
    normalize(14);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("Vir-96");
      assertEquals("Phikmvlikevirus: Pseudomonas phage LKA1 ICTV", t.name.getScientificName());
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
      NeoTaxon t = byID("Sys-2476");
      assertNotNull(t);
      assertEquals("Chagasia maculata", t.name.getScientificName());
    }
  }

  @Test
  @Ignore("external dependency")
  public void testGsdGithub() throws Exception {
    normalize(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/19.tar.gz"));
    writeToFile();
  }

  void writeToFile() throws Exception {
    // dump graph as TEXT file for debugging
    File f = new File("graphs/tree-acef.txt");
    Files.createParentDirs(f);
    Writer writer = new FileWriter(f);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.TEXT);
    writer.close();
    System.out.println("Wrote graph to " + f.getAbsolutePath());

    f = new File("graphs/tree-acef.dot");
    writer = new FileWriter(f);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT);
    writer.close();
    System.out.println("Wrote graph to " + f.getAbsolutePath());
  }

}
