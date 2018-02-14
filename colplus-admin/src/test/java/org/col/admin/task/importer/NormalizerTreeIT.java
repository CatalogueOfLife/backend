package org.col.admin.task.importer;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.NeoDbFactory;
import org.col.admin.task.importer.neo.printer.GraphFormat;
import org.col.admin.task.importer.neo.printer.PrinterUtils;
import org.col.api.vocab.DataFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests to normalize various dwc archives
 * and compare the results from the resulting neo store with an expected text tree representation stored as files.
 *
 * This exactly compares the parent_of and synonym_of relations, implicitly created names/taxa
 * and verifies that basionym relations are existing, but does not very the actual basionym itself
 * (which is checked in a manual test in NormalizerIT instead)
 */
@RunWith(Parameterized.class)
@Ignore
public class NormalizerTreeIT {
  final static int MAX_ACEF_ID = 1;
  final static int MAX_DWCA_ID = 23;

  private NeoDb store;
  private NormalizerConfig cfg;
  private Path source;

  //TODO: these 3 tests need to be checked - they do seem to create real wrong outcomes !!!
  Set<Integer> ignoreDwca = Sets.newHashSet(10, 18, 21);

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    IntStream acefIds = IntStream.rangeClosed(0, MAX_ACEF_ID);
    IntStream dwcaIds = IntStream.empty();
    //IntStream dwcaIds = IntStream.rangeClosed(0, MAX_DWCA_ID);
    return Stream.concat(
        acefIds.mapToObj(i -> new Object[]{DataFormat.ACEF, i}),
        dwcaIds.mapToObj(i -> new Object[]{DataFormat.DWCA, i})
    ).collect(Collectors.toList());
  }

  // test param
  private DataFormat format;
  private int datasetKey;

  public NormalizerTreeIT(DataFormat format, int datasetKey) {
    this.format = format;
    this.datasetKey = datasetKey;
  }

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    // make sure its empty
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      // store is close by Normalizer.run method already
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }

  /**
   * Normalizes a dwca from the dwca test resources and checks its printed txt tree against the expected tree
   */
  @Test
  public void testTree() throws Exception {
    if (format == DataFormat.DWCA && ignoreDwca.contains(datasetKey)) {
      System.out.println("IGNORE DWCA NORMALIZER TEST FOR DATASET "+datasetKey);
      return;
    }

    try {
      final String resourceDir = "/"+format.name().toLowerCase()+"/"+datasetKey;
      URL dwcaUrl = getClass().getResource(resourceDir);
      source = Paths.get(dwcaUrl.toURI());
      System.out.println("TEST " + format+" " + datasetKey);

      store = NeoDbFactory.create(cfg,datasetKey);

      Normalizer norm = new Normalizer(store, source.toFile(), format);
      try {
        norm.run();

      } finally {
        // reopen the neo db
        store = NeoDbFactory.open(cfg,datasetKey);
        debug();
      }

      // assert tree
      InputStream tree = getClass().getResourceAsStream(resourceDir+"/expected.tree");
      String expected = IOUtils.toString(tree, Charsets.UTF_8).trim();

      Writer writer = new StringWriter();
      PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.TEXT);
      String neotree = writer.toString().trim();
      assertFalse("Empty tree, probably no root node found", neotree.isEmpty());

      // compare trees
      assertEquals(expected, neotree);

    } catch (Exception e) {
      System.err.println("Failed to normalize " + format + " dataset " + datasetKey);
      throw e;
    }
  }

  void debug() throws Exception {
    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/tree"+datasetKey+".dot");
    Files.createParentDirs(dotFile);
    Writer writer = new FileWriter(dotFile);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT);
    writer.close();
    System.out.println("Wrote graph to "+dotFile.getAbsolutePath());
  }

}