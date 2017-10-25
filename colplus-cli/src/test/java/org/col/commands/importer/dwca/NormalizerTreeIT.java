package org.col.commands.importer.dwca;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.col.commands.config.NormalizerConfig;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.printer.GraphFormat;
import org.col.commands.importer.neo.printer.PrinterUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests to normalize various dwc archives
 * and compares the results from the neo store with an expected text tree representation stored as files.
 */
@RunWith(Parameterized.class)
public class NormalizerTreeIT {
  final static int MAX_DWCA_ID = 23;

  private NormalizerStore store;
  private NormalizerConfig cfg;
  private Path dwca;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    //TODO: use all tests when ready
    //IntStream stream = IntStream.rangeClosed(1, MAX_DWCA_ID);
    IntStream stream = IntStream.of(1,2,3,4,7,20);
    return stream
        .mapToObj(i -> new Object[]{i})
        .collect(Collectors.toList());
  }

  // test param
  private int datasetKey;

  public NormalizerTreeIT(int datasetKey) {
    this.datasetKey = datasetKey;
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

  /**
   * Normalizes a dwca from the dwca test resources and checks its printed txt tree against the expected tree
   */
  @Test
  public void testDwcaTree() throws Exception {
    try {
      URL dwcaUrl = getClass().getResource("/dwca/"+datasetKey);
      dwca = Paths.get(dwcaUrl.toURI());

      store = NeoDbFactory.create(cfg,datasetKey);

      Normalizer norm = new Normalizer(store, dwca.toFile());
      norm.run();

      // reopen the neo db
      store = NeoDbFactory.open(cfg,datasetKey);


      // assert tree
      InputStream tree = getClass().getResourceAsStream("/dwca/"+datasetKey+"/expected.tree");
      String expected = IOUtils.toString(tree, Charsets.UTF_8).trim();

      // dump graph as DOT file for debugging
      File dotFile = new File("graphs/tree"+datasetKey+".dot");
      Files.createParentDirs(dotFile);
      Writer writer = new FileWriter(dotFile);
      PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT);
      writer.close();
      System.out.println("Wrote graph to "+dotFile.getAbsolutePath());

      writer = new StringWriter();
      PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.TEXT);
      String neotree = writer.toString().trim();
      assertFalse("Empty tree, probably no root node found", neotree.isEmpty());

      // compare trees
      assertEquals(expected, neotree);

    } catch (Exception e) {
      System.err.println("Failed to normalize dataset "+datasetKey);
      throw e;
    }
  }

}