package org.col.admin.importer;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.Nullable;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NeoDbFactory;
import org.col.admin.importer.neo.NotUniqueRuntimeException;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.neo.model.RankedName;
import org.col.admin.importer.neo.printer.GraphFormat;
import org.col.admin.importer.neo.printer.PrinterUtils;
import org.col.admin.matching.NameIndexFactory;
import org.col.api.model.Dataset;
import org.col.api.model.IssueContainer;
import org.col.api.model.VerbatimEntity;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;

abstract class NormalizerITBase {
  
  protected NeoDb store;
  private NormalizerConfig cfg;
  private Path dwca;
  private final DataFormat format;
  
  NormalizerITBase(DataFormat format) {
    this.format = format;
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
      store.closeAndDelete();
    }
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  /**
   * Normalizes an archive from the test resources
   * and checks its printed txt tree against the expected tree
   *
   */
  public void normalize(int datasetKey) throws Exception {
    URL url = getClass().getResource("/" + format.name().toLowerCase() + "/" + datasetKey);
    normalize(Paths.get(url.toURI()));
  }

  public void normalize(URI url) throws Exception {
    // download an decompress
    ExternalSourceUtil.consumeSource(url, this::normalize);
  }

  private void normalize(Path arch) {
    try {
      store = NeoDbFactory.create(1, cfg);
      Dataset d = new Dataset();
      d.setKey(1);
      d.setDataFormat(format);
      store.put(d);
      Normalizer norm = new Normalizer(store, arch, NameIndexFactory.passThru());
      norm.call();

      // reopen
      store = NeoDbFactory.open(1, cfg);

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public VerbatimRecord vByID(String id) {
    return store.getVerbatim(store.names().objByID(id).getVerbatimKey());
  }
  
  
  public NeoUsage byName(String name) {
    return byName(name, null);
  }
  
  public NeoUsage byName(String name, @Nullable String author) {
    List<Node> nodes = store.names().nodesByName(name);
    // filter by author
    if (author != null) {
      nodes.removeIf(n -> !author.equalsIgnoreCase(NeoProperties.getAuthorship(n)));
    }

    if (nodes.isEmpty()) {
      throw new NotFoundException();
    }
    if (nodes.size() > 1) {
      throw new NotUniqueRuntimeException("scientificName", name);
    }
    return usageWithName(nodes.get(0));
  }
  
  public NeoUsage accepted(Node syn) {
    List<RankedName> accepted = store.accepted(syn);
    if (accepted.size() != 1) {
      throw new IllegalStateException("Synonym has " + accepted.size() + " accepted taxa");
    }
    return usageWithName(accepted.get(0).node);
  }
  
  public boolean hasIssues(VerbatimEntity ent, Issue... issues) {
    IssueContainer ic = store.getVerbatim(ent.getVerbatimKey());
    for (Issue is : issues) {
      if (!ic.hasIssue(is))
        return false;
    }
    return true;
  }
  
  public NeoUsage usageWithName(Node n) {
    NeoUsage u = store.usages().objByNode(n);
    NeoName nn = store.names().objByNode(n);
    u.usage.setName(nn.name);
    return u;
  }
  
  public NeoUsage byID(String id) {
    return usageWithName(store.usages().nodeByID(id));
  }
  
  public void debug() throws Exception {
    PrinterUtils.printTree(store.getNeo(), new PrintWriter(System.out), GraphFormat.TEXT, true);

    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/debugtree.dot");
    Files.createParentDirs(dotFile);
    Writer writer = new FileWriter(dotFile);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT, true);
    writer.close();
    System.out.println("Wrote graph to " + dotFile.getAbsolutePath());
  }

}
