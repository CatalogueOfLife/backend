package org.col.admin.task.importer;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.NeoDbFactory;
import org.col.admin.task.importer.neo.NotUniqueRuntimeException;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.printer.GraphFormat;
import org.col.admin.task.importer.neo.printer.PrinterUtils;
import org.col.api.model.*;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.Language;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerDwcaIT {
  private NeoDb store;
  private NormalizerConfig cfg;
  private Path dwca;

  /**
   * Normalizes a dwca from the dwca test resources and checks its printed txt tree against the expected tree
   * @param datasetKey
   * @return
   * @throws Exception
   */
  private void normalize(int datasetKey) throws Exception {
    URL dwcaUrl = getClass().getResource("/dwca/"+datasetKey);
    normalize(Paths.get(dwcaUrl.toURI()));
  }

  private void normalize(URI url) throws Exception {
    // download an decompress
    ExternalSourceUtil.consumeSource(url, this::normalize);
  }

  private void normalize(Path dwca) {
    try {
      store = NeoDbFactory.create(1, cfg);
      Dataset d = new Dataset();
      d.setKey(1);
      d.setDataFormat(DataFormat.DWCA);
      store.put(d);
      Normalizer norm = new Normalizer(store, dwca);
      norm.run();

      // reopen
      store = NeoDbFactory.open(1, cfg);

    } catch (IOException e) {
      throw new RuntimeException(e);
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

  NeoTaxon byName(String name) {
    return byName(name, null);
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
  public void testBdjCsv() throws Exception {
    normalize(17);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("1099-sp16");
      assertFalse(t.isSynonym());
      assertEquals("Pinus palustris Mill.", t.name.canonicalNameComplete());
      assertEquals(URI.create("http://dx.doi.org/10.3897/BDJ.2.e1099"), t.taxon.getDatasetUrl());
    }
  }

  @Test
  public void testPublishedIn() throws Exception {
    normalize(0);

    for (Reference r : store.refList()) {
      System.out.println(r);
    }

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon trametes_modesta = byID("324805");
      trametes_modesta = byID("324805");
      assertFalse(trametes_modesta.isSynonym());
      assertEquals(1, trametes_modesta.acts.size());

      Reference pubIn = store.refByKey(trametes_modesta.acts.get(0).getReferenceKey());
      assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getTitle());
      assertNotNull(pubIn.getKey());
      assertNull(pubIn.getId());

      NeoTaxon Polystictus_substipitatus = byID("140283");
      assertTrue(Polystictus_substipitatus.isSynonym());
      assertEquals(1, Polystictus_substipitatus.synonym.getAccepted().size());
      assertEquals(1, Polystictus_substipitatus.acts.size());

      NeoTaxon Polyporus_modestus = byID("198666");
      assertTrue(Polyporus_modestus.isSynonym());
      assertEquals(1, Polyporus_modestus.synonym.getAccepted().size());
      assertEquals(1, Polyporus_modestus.acts.size());
    }
  }

  @Test
  public void testSupplementary() throws Exception {
    normalize(24);

    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // check species name
      NeoTaxon t = byID("1000");
      assertEquals("Crepis pulchra", t.name.getScientificName());

      // check vernaculars
      Map<Language, String> expV = jersey.repackaged.com.google.common.collect.Maps.newHashMap();
      expV.put(Language.GERMAN, "Schöner Pippau");
      expV.put(Language.ENGLISH, "smallflower hawksbeard");
      assertEquals(expV.size(), t.vernacularNames.size());
      for (VernacularName vn : t.vernacularNames) {
        assertEquals(expV.remove(vn.getLanguage()), vn.getName());
      }
      assertTrue(expV.isEmpty());

      // check distributions
      Set<Distribution> expD = Sets.newHashSet();
      expD.add(dist(Gazetteer.TEXT, "All of Austria and the alps", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "DE", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "FR", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "DK", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "GB", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "NG", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "KE", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "AGS", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.FAO, "37.4.1", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "MOR-MO", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "MOR-CE", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "MOR-ME", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "CPP", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "NAM", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "IT-82", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "ES-CN", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "FR-H", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "FM-PNI", DistributionStatus.NATIVE));

      assertEquals(expD.size(), t.distributions.size());
      // remove dist keys before we check equality
      t.distributions.forEach(d -> d.setKey(null));
      Set<Distribution> imported = Sets.newHashSet(t.distributions);

      Sets.SetView<Distribution> diff = Sets.difference(expD, imported);
      for (Distribution d : diff) {
        System.out.println(d);
      }
      assertEquals(expD, imported);
    }
  }

  /**
   * https://github.com/Sp2000/colplus-backend/issues/69
   */
  @Test
  public void testIcznLists() throws Exception {
    normalize(26);

    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // check species name
      NeoTaxon t = byID("10156");
      assertEquals("'Prosthète'", t.name.getScientificName());
    }
  }

  private Distribution dist(Gazetteer standard, String area, DistributionStatus status) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setGazetteer(standard);
    d.setStatus(status);
    return d;
  }

  @Test
  public void testNeoIndices() throws Exception {
    normalize(1);

    Set<String> taxonIndices = Sets.newHashSet();
    taxonIndices.add(NeoProperties.ID);
    taxonIndices.add(NeoProperties.TAXON_ID);
    taxonIndices.add(NeoProperties.SCIENTIFIC_NAME);
    try (Transaction tx = store.getNeo().beginTx()) {
      Schema schema = store.getNeo().schema();
      for (IndexDefinition idf : schema.getIndexes(Labels.TAXON)) {
        List<String> idxProps = Iterables.asList(idf.getPropertyKeys());
        assertTrue(idxProps.size() == 1);
        assertTrue(taxonIndices.remove(idxProps.get(0)));
      }

      // 1001, Crepis bakeri Greene
      assertNotNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.ID, "1001")));
      assertNotNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "Crepis bakeri")));

      assertNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.ID, "x1001")));
      assertNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "xCrepis bakeri")));
    }
  }

  @Test
  public void testBasionym() throws Exception {
    normalize(1);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon u1 = byID("1006");
      NeoTaxon u2 = byName("Leontodon taraxacoides", "(Vill.) Mérat");

      assertEquals(u1, u2);

      NeoTaxon bas = byName("Leonida taraxacoida");
      assertEquals(u2.name.getBasionymKey(), bas.taxon.getKey());

      NeoTaxon syn = byName("Leontodon leysseri");
      assertEquals(1, syn.synonym.getAccepted().size());
      NeoTaxon acc = byID("1006");
      assertEquals(acc.taxon.getId(), syn.synonym.getAccepted().get(0).getId());
      assertEquals(acc.taxon.getKey(), syn.synonym.getAccepted().get(0).getKey());

    }
  }

  private void debug() throws Exception {
    PrinterUtils.printTree(store.getNeo(), new PrintWriter(System.out), GraphFormat.TEXT);

    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/dbugtree.dot");
    Files.createParentDirs(dotFile);
    Writer writer = new FileWriter(dotFile);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT);
    writer.close();
    System.out.println("Wrote graph to "+dotFile.getAbsolutePath());
  }

  @Test
  public void testProParte() throws Exception {
    normalize(8);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon syn = byID("1001");
      assertEquals(3, syn.synonym.getAccepted().size());

      Map<String, String> expectedAccepted = Maps.newHashMap();
      expectedAccepted.put("1000", "Calendula arvensis");
      expectedAccepted.put("10000", "Calendula incana subsp. incana");
      expectedAccepted.put("10002", "Calendula incana subsp. maderensis");

      for (Taxon acc : syn.synonym.getAccepted()) {
        assertEquals(expectedAccepted.remove(acc.getId()), acc.getName().getScientificName());
      }
      assertTrue(expectedAccepted.isEmpty());
    }
  }

  @Test
  @Ignore
  public void testExternal() throws Exception {
    normalize(Paths.get("/Users/markus/Desktop/worms"));
    //normalize(URI.create("http://www.marinespecies.org/dwca/WoRMS_DwC-A.zip"));
    //print("Diversity", GraphFormat.TEXT, false);
  }

  void print(String id, GraphFormat format, boolean file) throws Exception {
    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/tree-dwca-"+id+"."+format.suffix);
    Files.createParentDirs(dotFile);
    Writer writer;
    if (file) {
      writer = new FileWriter(dotFile);
    } else {
      writer = new StringWriter();
    }
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT);
    writer.close();

    if (file) {
      System.out.println("Wrote graph to "+dotFile.getAbsolutePath());
    } else {
      System.out.println(writer.toString());
    }
  }

}