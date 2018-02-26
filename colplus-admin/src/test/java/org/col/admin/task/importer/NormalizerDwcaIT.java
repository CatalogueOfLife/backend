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
import org.col.api.model.Reference;
import org.col.api.model.Taxon;
import org.col.api.vocab.DataFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
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
  private void normalizeDwca(int datasetKey) throws Exception {
    URL dwcaUrl = getClass().getResource("/dwca/"+datasetKey);
    dwca = Paths.get(dwcaUrl.toURI());

    store = NeoDbFactory.create(datasetKey, cfg);

    Normalizer norm = new Normalizer(store, dwca, DataFormat.DWCA);
    norm.run();

    // reopen
    store = NeoDbFactory.open(datasetKey, cfg);
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

  NeoTaxon byTaxonID(String id) {
    Node n = store.byTaxonID(id);
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
  public void testPublishedIn() throws Exception {
    normalizeDwca(0);

    for (Reference r : store.refList()) {
      System.out.println(r);
    }

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon trametes_modesta = byTaxonID("324805");
      trametes_modesta = byTaxonID("324805");
      assertFalse(trametes_modesta.isSynonym());
      assertEquals(1, trametes_modesta.acts.size());

      Reference pubIn = store.refByKey(trametes_modesta.acts.get(0).getReferenceKey());
      assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getTitle());
      assertNotNull(pubIn.getKey());
      assertNull(pubIn.getId());

      NeoTaxon Polystictus_substipitatus = byTaxonID("140283");
      assertTrue(Polystictus_substipitatus.isSynonym());
      assertEquals(1, Polystictus_substipitatus.synonym.accepted.size());
      assertEquals(1, Polystictus_substipitatus.acts.size());

      NeoTaxon Polyporus_modestus = byTaxonID("198666");
      assertTrue(Polyporus_modestus.isSynonym());
      assertEquals(1, Polyporus_modestus.synonym.accepted.size());
      assertEquals(1, Polyporus_modestus.acts.size());
    }
  }

  @Test
  public void testNeoIndices() throws Exception {
    normalizeDwca(1);

    Set<String> taxonIndices = Sets.newHashSet();
    taxonIndices.add(NeoProperties.TAXON_ID);
    taxonIndices.add(NeoProperties.SCIENTIFIC_NAME);
    taxonIndices.add(NeoProperties.NAME_ID);
    try (Transaction tx = store.getNeo().beginTx()) {
      Schema schema = store.getNeo().schema();
      for (IndexDefinition idf : schema.getIndexes(Labels.TAXON)) {
        List<String> idxProps = Iterables.asList(idf.getPropertyKeys());
        assertTrue(idxProps.size() == 1);
        assertTrue(taxonIndices.remove(idxProps.get(0)));
      }

      // 1001, Crepis bakeri Greene
      assertNotNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.TAXON_ID, "1001")));
      assertNotNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "Crepis bakeri")));

      assertNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.TAXON_ID, "x1001")));
      assertNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "xCrepis bakeri")));
    }
  }

  @Test
  public void testBasionym() throws Exception {
    normalizeDwca(1);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon u1 = byTaxonID("1006");
      NeoTaxon u2 = byName("Leontodon taraxacoides", "(Vill.) Mérat");
      assertEquals(u1, u2);

      NeoTaxon bas = byName("Leonida taraxacoida");
      assertEquals(u2.name.getBasionymKey(), bas.taxon.getKey());

      NeoTaxon syn = byName("Leontodon leysseri");
      assertEquals(1, syn.synonym.accepted.size());
      NeoTaxon acc = byTaxonID("1006");
      assertEquals(acc.taxon.getId(), syn.synonym.accepted.get(0).getId());
      assertEquals(acc.taxon.getKey(), syn.synonym.accepted.get(0).getKey());

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
    normalizeDwca(8);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon syn = byTaxonID("1001");
      assertEquals(3, syn.synonym.accepted.size());

      Map<String, String> expectedAccepted = Maps.newHashMap();
      expectedAccepted.put("1000", "Calendula arvensis");
      expectedAccepted.put("10000", "Calendula incana subsp. incana");
      expectedAccepted.put("10002", "Calendula incana subsp. maderensis");

      for (Taxon acc : syn.synonym.accepted) {
        assertEquals(expectedAccepted.remove(acc.getId()), acc.getName().getScientificName());
      }
      assertTrue(expectedAccepted.isEmpty());
    }
  }

}