package life.catalogue.importer;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
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
import life.catalogue.api.vocab.DatasetSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.importer.neo.model.RankedName;
import life.catalogue.importer.neo.printer.PrinterUtils;
import life.catalogue.matching.NameIndexFactory;
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
 * <p>
 * This exactly compares the parent_of and synonym_of relations, implicitly created names/taxa and
 * verifies that basionym relations are existing, but does not very the actual basionym itself
 * (which is checked in a manual test in NormalizerIT instead)
 */
@RunWith(Parameterized.class)
public class NormalizerTreeIT {
  final static int MAX_ACEF_ID = 7;
  final static int MAX_DWCA_ID = 34;
  final static int MAX_COLDP_ID = 0;

  private static NormalizerConfig cfg;
  private NeoDb store;
  private Path source;
  
  // TODO: these tests need to be checked - they do seem to create real wrong outcomes !!!
  Set<Integer> ignoreAcef = Sets.newHashSet(3);
  Set<Integer> ignoreDwca = Sets.newHashSet(21);
  Set<Integer> ignoreColdp = Sets.newHashSet();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    IntStream acefIds = IntStream.rangeClosed(0, MAX_ACEF_ID);
    IntStream dwcaIds = IntStream.rangeClosed(0, MAX_DWCA_ID);
    IntStream coldpIds = IntStream.rangeClosed(0, MAX_COLDP_ID);

    //acefIds = IntStream.empty();
    //acefIds = IntStream.of(0);
    //dwcaIds = IntStream.empty();
    //dwcaIds = IntStream.of(31, 32, 33, 34);
    //coldpIds = IntStream.empty();

    return Stream.concat(
        acefIds.mapToObj(i -> new Object[] {DataFormat.ACEF, i}),
        Stream.concat(
            dwcaIds.mapToObj(i -> new Object[] {DataFormat.DWCA, i}),
            coldpIds.mapToObj(i -> new Object[] {DataFormat.COLDP, i})
        )
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
      
      store = NeoDbFactory.create(datasetKey, 1, cfg);

      Dataset d = new Dataset();
      d.setKey(datasetKey);
      d.setDataFormat(format);
      // check if dataset.yaml file exists for extended dataset properties
      NormalizerITBase.readDatasetCode(resourceDir).ifPresent(code -> d.putSetting(DatasetSettings.NOMENCLATURAL_CODE, code));
      
      store.put(d);
      
      Normalizer norm = new Normalizer(format, store, source, NameIndexFactory.passThru(), ImageService.passThru());
      norm.call();
      // reopen the neo db
      store = NeoDbFactory.open(datasetKey, 1, cfg);
      debug();
      
      // assert tree
      InputStream tree = getClass().getResourceAsStream(resourceDir + "/expected.tree");
      String expected = IOUtils.toString(tree, Charsets.UTF_8).trim();
      
      String neotree = PrinterUtils.textTree(store.getNeo());
      assertFalse("Empty tree, probably no root node found", neotree.isEmpty());
      
      // compare trees
      assertEquals("Taxon tree not as expected", expected, neotree);

      // queue non tree nodes
      String bareNames;
      try (Transaction tx = store.getNeo().beginTx()) {
        bareNames = store.bareNameNodes()
            .map(NeoProperties::getRankedName)
            .map(RankedName::toString)
            .sorted()
            .collect(Collectors.joining("\n"));
      }

      InputStream bareNamesFile = getClass().getResourceAsStream(resourceDir + "/expected-barenames.txt");
      if (bareNamesFile != null) {
        expected = IOUtils.toString(bareNamesFile, Charsets.UTF_8).trim();
        assertEquals("Bare names not as expected", expected, bareNames);
      } else if (!StringUtils.isBlank(bareNames)) {
        fail("Additional bare names:\n" + bareNames);
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
    PrinterUtils.dumpDotFile(store.getNeo(), writer);
    writer.close();
    System.out.println("Wrote graph to " + dotFile.getAbsolutePath());
  }
  
}
