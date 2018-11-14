package org.col.admin.importer;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NeoDbFactory;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.printer.GraphFormat;
import org.col.admin.importer.neo.printer.PrinterUtils;
import org.col.admin.matching.NameIndexFactory;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.*;

/**
 * Tests to normalize various dwc archives and compare the results from the resulting neo store with
 * an expected text tree representation stored as files.
 *
 * This exactly compares the parent_of and synonym_of relations, implicitly created names/taxa and
 * verifies that basionym relations are existing, but does not very the actual basionym itself
 * (which is checked in a manual test in NormalizerIT instead)
 */
@RunWith(Parameterized.class)
public class NormalizerTreeIT {
  final static int MAX_ACEF_ID = 7;
  final static int MAX_DWCA_ID = 31;

  private static NormalizerConfig cfg;
  private NeoDb store;
  private Path source;

  // TODO: these tests need to be checked - they do seem to create real wrong outcomes !!!
  Set<Integer> ignoreAcef = Sets.newHashSet(3);
  Set<Integer> ignoreDwca = Sets.newHashSet(21);

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    IntStream acefIds = IntStream.rangeClosed(0, MAX_ACEF_ID);
    IntStream dwcaIds = IntStream.rangeClosed(0, MAX_DWCA_ID);

    // acefIds = IntStream.empty();
    // acefIds = IntStream.of(6,7);
    // dwcaIds = IntStream.empty();
    // dwcaIds = IntStream.of(31);

    return Stream.concat(acefIds.mapToObj(i -> new Object[] {DataFormat.ACEF, i}),
        dwcaIds.mapToObj(i -> new Object[] {DataFormat.DWCA, i})).collect(Collectors.toList());
  }

  private static AtomicInteger keyGen = new AtomicInteger(1);

  // test param
  private DataFormat format;
  private int sourceKey;

  public NormalizerTreeIT(DataFormat format, int sourceKey) {
    this.format = format;
    this.sourceKey = sourceKey;
  }

  @BeforeClass
  public static void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    // make sure its empty
    FileUtils.cleanDirectory(cfg.archiveDir);
    FileUtils.cleanDirectory(cfg.scratchDir);
  }

  @AfterClass
  public static void cleanupRepo() throws Exception {
    System.out.println("Removing temp test repo");
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      store.closeAndDelete();
    }
  }

  /**
   * Normalizes a dwca from the dwca test resources and checks its printed txt tree against the
   * expected tree
   */
  @Test
  public void testTree() throws Exception {
    final int datasetKey = keyGen.incrementAndGet();

    if (format == DataFormat.ACEF && ignoreAcef.contains(sourceKey)
        || format == DataFormat.DWCA && ignoreDwca.contains(sourceKey)) {
      System.out.println("IGNORE " + format + " NORMALIZER TEST FOR SOURCE " + sourceKey);
      return;
    }

    try {
      final String resourceDir = "/" + format.name().toLowerCase() + "/" + sourceKey;
      URL dwcaUrl = getClass().getResource(resourceDir);
      source = Paths.get(dwcaUrl.toURI());
      System.out.println("TEST " + format + " " + sourceKey);

      store = NeoDbFactory.create(datasetKey, cfg);
      Dataset d = new Dataset();
      d.setKey(datasetKey);
      d.setDataFormat(format);
      store.put(d);

      Normalizer norm = new Normalizer(store, source, NameIndexFactory.passThru());
      norm.call();
      // reopen the neo db
      store = NeoDbFactory.open(datasetKey, cfg);
      // debug();
      
      // assert tree
      InputStream tree = getClass().getResourceAsStream(resourceDir + "/expected.tree");
      String expected = IOUtils.toString(tree, Charsets.UTF_8).trim();

      Writer writer = new StringWriter();
      PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.TEXT);
      String neotree = writer.toString().trim();
      assertFalse("Empty tree, probably no root node found", neotree.isEmpty());

      // compare trees
      assertEquals(expected, neotree);

      // list non tree nodes
      Set<Long> ids = store.nodeIdsOutsideTree();
      List<String> names = new ArrayList<>(ids.size());
      try (Transaction tx = store.getNeo().beginTx()) {
        for (Long id : ids) {
          names.add(NeoProperties.getRankedName(store.getNeo().getNodeById(id)).toString());
        }
      }
      Collections.sort(names);
      String namesStr = Joiner.on('\n').join(names);
      InputStream namesFile =
          getClass().getResourceAsStream(resourceDir + "/expected-barenames.txt");
      if (namesFile != null) {
        expected = IOUtils.toString(namesFile, Charsets.UTF_8).trim();
        assertEquals(expected, namesStr);
      } else if (!names.isEmpty()) {
        fail("Additional Bare Names:\n" + namesStr);
      }

    } catch (Exception e) {
      System.err.println("Failed to normalize " + format + " dataset " + sourceKey);
      throw e;
    }
  }

  void debug() throws Exception {
    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/tree-" + format + sourceKey + ".dot");
    Files.createParentDirs(dotFile);
    Writer writer = new FileWriter(dotFile);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT);
    writer.close();
    System.out.println("Wrote graph to " + dotFile.getAbsolutePath());
  }

}
