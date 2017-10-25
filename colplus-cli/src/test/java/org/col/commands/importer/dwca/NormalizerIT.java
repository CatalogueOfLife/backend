package org.col.commands.importer.dwca;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.col.commands.config.NormalizerConfig;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.NotUniqueRuntimeException;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.NeoProperties;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.commands.importer.neo.printer.GraphFormat;
import org.col.commands.importer.neo.printer.PrinterUtils;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerIT {
  private NormalizerStore store;
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
    dwca = Paths.get(dwcaUrl.toURI());

    store = NeoDbFactory.create(cfg,datasetKey);

    Normalizer norm = new Normalizer(store, dwca.toFile());
    norm.run();

    reopen(datasetKey);
    assertTree(datasetKey);
  }

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.directory = Files.createTempDir();
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      // store is close by Normalizer.run method already
      FileUtils.deleteQuietly(cfg.directory);
    }
  }

  void reopen(int datasetKey) throws IOException {
    store = NeoDbFactory.open(cfg,datasetKey);
  }

  NeoTaxon byNodeId(long id) {
    Node n = store.getNeo().getNodeById(id);
    return store.get(n);
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

  private void assertTree(int datasetKey) throws Exception {
    System.out.println("assert tree");

    InputStream tree = getClass().getResourceAsStream("/trees/dwca-"+datasetKey+".txt");
    String expected = IOUtils.toString(tree, Charsets.UTF_8);

    StringWriter buffer = new StringWriter();
    PrinterUtils.printTree(store.getNeo(), buffer, GraphFormat.TEXT);

    // compare trees
    assertEquals(expected, buffer.toString().trim());
  }

  @Test
  @Ignore("Need to fix name parsing first")
  public void testNeoIndices() throws Exception {
    normalize(1);

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
  public void testIdList() throws Exception {
    normalize(1);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon u1 = byTaxonID("1006");
      NeoTaxon u2 = byName("Leontodon taraxacoides", "(Vill.) Mérat");
//      NeoTaxon u3 = byNodeId(u1.taxon.getKey());

      assertEquals(u1, u2);
//      assertEquals(u1, u3);

      NeoTaxon syn = byName("Leontodon leysseri");
      NeoTaxon acc = byTaxonID("1006");
      assertEquals(acc.taxon.getId(), syn.synonym.acceptedNameUsageID);

      // TODO: metrics
     // assertMetrics(getMetricsByTaxonId("101"), 2, 2, 0, 0, 0, 0, 0, 0, 0, 2);
     // assertMetrics(getMetricsByTaxonId("1"), 1, 15, 1, 0, 0, 0, 1, 4, 0, 7);
    }
  }

}