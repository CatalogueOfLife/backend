package org.col.admin.task.importer;

import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
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
import org.col.util.CompressionUtil;
import org.col.util.DownloadUtil;
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
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
    // download file
    HttpClientBuilder htb = HttpClientBuilder.create();
    File tmp = File.createTempFile("col-gsd", ".tar.gz");
    Path source = java.nio.file.Files.createTempDirectory("col-gsd");
    try (CloseableHttpClient hc = htb.build()) {
      DownloadUtil down = new DownloadUtil(hc);
      down.downloadIfModified(url, tmp);
      // decompress into folder
      CompressionUtil.decompressFile(source.toFile(), tmp);
      // normalize
      normalize(source);

    } finally {
      FileUtils.deleteQuietly(tmp);
      MoreFiles.deleteRecursively(source, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }

  private void normalize(Path source) throws Exception {
    acef = source;

    store = NeoDbFactory.create(cfg, 1);

    Normalizer norm = new Normalizer(store, acef.toFile(), DataFormat.ACEF);
    norm.run();

    // reopen
    store = NeoDbFactory.open(cfg, 1);
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
    Node n = store.byTaxonID(id);
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
  public void testAcefSample() throws Exception {
    normalize(1);

    for (Reference r : store.refList()) {
      System.out.println(r);
    }

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("14649");
      assertEquals("Zapoteca formosa", t.name.getScientificName());
      assertEquals("(Kunth) H.M.Hern.", t.name.authorshipComplete());
      assertEquals(Rank.SPECIES, t.name.getRank());
      assertEquals("Fabaceae", t.classification.getFamily());

      t = byName("Fabaceae",null);
      assertEquals("Fabaceae", t.name.getScientificName());
      assertEquals("Fabaceae", t.name.canonicalNameComplete());
      assertNull(t.name.authorshipComplete());
      assertEquals(Rank.FAMILY, t.name.getRank());
    }
  }

  @Test
  @Ignore
  public void testGsdGithub() throws Exception {
    normalize(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/assembly/73.tar.gz"));
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