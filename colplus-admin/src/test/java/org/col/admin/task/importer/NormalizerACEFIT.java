package org.col.admin.task.importer;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.NeoDbFactory;
import org.col.admin.task.importer.neo.NotUniqueRuntimeException;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.printer.GraphFormat;
import org.col.admin.task.importer.neo.printer.PrinterUtils;
import org.col.api.model.Reference;
import org.col.api.vocab.DataFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
    acef = Paths.get(acefUrl.toURI());

    store = NeoDbFactory.create(cfg,datasetKey);

    Normalizer norm = new Normalizer(store, acef.toFile(), DataFormat.ACEF);
    norm.run();

    // reopen
    store = NeoDbFactory.open(cfg,datasetKey);
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
  public void testAcefSample() throws Exception {
    normalize(1);

    for (Reference r : store.refList()) {
      System.out.println(r);
    }

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon trametes_modesta = byTaxonID("324805");
      assertEquals(1, trametes_modesta.acts.size());

      Reference pubIn = store.refByKey(trametes_modesta.acts.get(0).getReferenceKey());
      assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getTitle());
      assertNotNull(pubIn.getKey());
      assertNull(pubIn.getId());
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

}