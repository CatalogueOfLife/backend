package org.col.admin.task.importer;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import org.col.admin.task.importer.reference.ReferenceFactory;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.csl.CslParserMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
public class  NormalizerTreeIT {
  final static int MAX_ACEF_ID = 5;
  final static int MAX_DWCA_ID = 28;

  private NeoDb store;
  private NormalizerConfig cfg;
  private Path source;

  //TODO: these 3 tests need to be checked - they do seem to create real wrong outcomes !!!
  Set<Integer> ignoreAcef= Sets.newHashSet(3);
  Set<Integer> ignoreDwca = Sets.newHashSet(10, 18, 21);

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    IntStream acefIds = IntStream.rangeClosed(0, MAX_ACEF_ID);
    IntStream dwcaIds = IntStream.rangeClosed(0, MAX_DWCA_ID);

    //acefIds = IntStream.empty();
    //dwcaIds = IntStream.rangeClosed(23, 27);
    return Stream.concat(
        acefIds.mapToObj(i -> new Object[]{DataFormat.ACEF, i}),
        dwcaIds.mapToObj(i -> new Object[]{DataFormat.DWCA, i})
    ).collect(Collectors.toList());
  }

  private static AtomicInteger keyGen = new AtomicInteger(1);

  // test param
  private DataFormat format;
  private int sourceKey;

  public NormalizerTreeIT(DataFormat format, int sourceKey) {
    this.format = format;
    this.sourceKey = sourceKey;
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
    final int datasetKey = keyGen.incrementAndGet();

    if (   format == DataFormat.ACEF && ignoreAcef.contains(sourceKey)
        || format == DataFormat.DWCA && ignoreDwca.contains(sourceKey)) {
      System.out.println("IGNORE "+format+" NORMALIZER TEST FOR SOURCE "+sourceKey);
      return;
    }

    try {
      final String resourceDir = "/"+format.name().toLowerCase()+"/"+sourceKey;
      URL dwcaUrl = getClass().getResource(resourceDir);
      source = Paths.get(dwcaUrl.toURI());
      System.out.println("TEST " + format+" " + sourceKey);

      store = NeoDbFactory.create(datasetKey, cfg);
      Dataset d = new Dataset();
      d.setKey(datasetKey);
      d.setDataFormat(format);
      store.put(d);

      Normalizer norm = new Normalizer(store, source, new ReferenceFactory(d.getKey(), new CslParserMock()));
      try {
        norm.run();

      } finally {
        // reopen the neo db
        store = NeoDbFactory.open(datasetKey, cfg);
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
      System.err.println("Failed to normalize " + format + " dataset " + sourceKey);
      throw e;
    }
  }

  void debug() throws Exception {
    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/tree-"+format+sourceKey+".dot");
    Files.createParentDirs(dotFile);
    Writer writer = new FileWriter(dotFile);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT);
    writer.close();
    System.out.println("Wrote graph to "+dotFile.getAbsolutePath());
  }

}