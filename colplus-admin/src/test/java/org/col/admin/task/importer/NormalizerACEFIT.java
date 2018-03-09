package org.col.admin.task.importer;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.NeoDbFactory;
import org.col.admin.task.importer.neo.NotUniqueRuntimeException;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.printer.GraphFormat;
import org.col.admin.task.importer.neo.printer.PrinterUtils;
import org.col.api.model.Dataset;
import org.col.api.model.Distribution;
import org.col.api.model.VernacularName;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;

import javax.annotation.Nullable;
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

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerACEFIT {
  private NeoDb store;
  private NormalizerConfig cfg;
  private Path acef;

  /**
   * Normalizes a ACEF folder from the test resources and checks its printed txt tree against the expected tree
   * @param datasetKey
   */
  private void normalize(int datasetKey) throws Exception {
    URL acefUrl = getClass().getResource("/acef/"+datasetKey);
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

      Normalizer norm = new Normalizer(store, acef);
      norm.run();

      // reopen
      store = NeoDbFactory.open(1, cfg);

    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      // store is close by Normalizer.run method already
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
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
    if (nodes.size()>1) {
      throw new NotUniqueRuntimeException("scientificName", name);
    }
    return store.get(nodes.get(0));
  }

  @Test
  public void acef0() throws Exception {
    normalize(0);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("s7");
      assertEquals("Astragalus nonexistus", t.name.getScientificName());
      assertEquals("DC.", t.name.authorshipComplete());
      assertEquals(Rank.SPECIES, t.name.getRank());
      assertTrue(t.issues.contains(Issue.ACCEPTED_ID_INVALID));
      assertTrue(t.classification.isEmpty());

      // vernacular
      assertEquals(1, t.vernacularNames.size());
      VernacularName v = t.vernacularNames.get(0);
      assertEquals("Non exiusting bean", v.getName());
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
        assertEquals(v.getName().startsWith("R") ? Language.HINDI : Language.RUSSIAN, v.getLanguage());
        assertTrue(names.remove(v.getName()));
      }

      // denormed family
      t = byName("Fabaceae",null);
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
      assertTrue(t.issues.contains(Issue.ID_NOT_UNIQUE));
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

  /**
   * ICTV GSD with "parsed" virus names
   * https://github.com/Sp2000/colplus-backend/issues/65
   */
  @Test
  public void acef14virus() throws Exception {
    normalize(14);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("Vir-96");
      assertEquals("Phikmvlikevirus: Pseudomonas phage LKA1 ICTV", t.name.getScientificName());

      store.all().forEach(nt -> {
        if (nt.name.getOrigin() == Origin.SOURCE) {
          if (nt.getID().equalsIgnoreCase("Vir-999")) {
            assertEquals(NameType.PLACEHOLDER, nt.name.getType());
            assertEquals("Unassigned: Banana virus X ICTV", nt.name.getScientificName());
            assertFalse(nt.name.isParsed());
            assertFalse(nt.name.hasAuthorship());

          } else {
            assertEquals(NameType.VIRUS, nt.name.getType());
            assertNotNull(nt.name.getScientificName());
            assertFalse(nt.name.isParsed());
            assertFalse(nt.name.hasAuthorship());
          }
        }
      });
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
    normalize(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/assembly/14.tar.gz"));
    writeToFile();
  }

  void writeToFile() throws Exception {
    // dump graph as TEXT file for debugging
    File f = new File("graphs/tree-acef.txt");
    Files.createParentDirs(f);
    Writer writer = new FileWriter(f);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.TEXT);
    writer.close();
    System.out.println("Wrote graph to "+f.getAbsolutePath());
  }

}