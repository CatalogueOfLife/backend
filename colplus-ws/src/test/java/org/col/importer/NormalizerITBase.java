package org.col.importer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.config.NormalizerConfig;
import org.col.img.ImageService;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NeoDbFactory;
import org.col.importer.neo.NotUniqueRuntimeException;
import org.col.importer.neo.model.NeoName;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.neo.model.RankedUsage;
import org.col.importer.neo.traverse.Traversals;
import org.col.matching.NameIndex;
import org.col.matching.NameIndexFactory;
import org.col.api.model.Dataset;
import org.col.api.model.IssueContainer;
import org.col.api.model.VerbatimEntity;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;
import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;

import static org.junit.Assert.*;

abstract class NormalizerITBase {
  
  protected NeoDb store;
  private NormalizerConfig cfg;
  private final DataFormat format;
  private final Supplier<NameIndex> nameIndexSupplier;
  
  NormalizerITBase(DataFormat format, Supplier<NameIndex> supplier) {
    this.format = format;
    nameIndexSupplier = supplier;
  }

  NormalizerITBase(DataFormat format) {
    this.format = format;
    nameIndexSupplier = NameIndexFactory::passThru;
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

  protected void normalize(Path arch) {
    try {
      store = NeoDbFactory.create(1, 1, cfg);
      Dataset d = new Dataset();
      d.setKey(1);
      d.setDataFormat(format);
      d.setNamesIndexContributor(true);
      store.put(d);
      Normalizer norm = new Normalizer(store, arch, nameIndexSupplier.get(), ImageService.passThru());
      norm.call();

      // reopen
      store = NeoDbFactory.open(1, 1,  cfg);

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
  
  public VerbatimRecord vByLine(Term type, long line) {
    for (VerbatimRecord v : store.verbatimList()) {
      if (v.getType() == type && v.getLine() == line) {
        return v;
      }
    }
    return null;
  }

  public VerbatimRecord vByNameID(String id) {
    return store.getVerbatim(store.names().objByID(id).getVerbatimKey());
  }
  
  public VerbatimRecord vByUsageID(String id) {
    return store.getVerbatim(store.usages().objByID(id).getVerbatimKey());
  }
  
  
  public NeoUsage byName(String name) {
    return byName(name, null);
  }
  
  public NeoUsage byName(String name, @Nullable String author) {
    List<Node> usageNodes = store.usagesByName(name, author, null, true);
    if (usageNodes.isEmpty()) {
      throw new NotFoundException();
    }
    if (usageNodes.size() > 1) {
      throw new NotUniqueRuntimeException("scientificName", name);
    }
    return store.usageWithName(usageNodes.get(0));
  }
  
  public NeoUsage accepted(Node syn) {
    List<RankedUsage> accepted = store.accepted(syn);
    if (accepted.size() != 1) {
      throw new IllegalStateException("Synonym has " + accepted.size() + " accepted taxa");
    }
    return store.usageWithName(accepted.get(0).usageNode);
  }
  
  public List<NeoUsage> parents(Node child, String... parentIdsToVerify) {
    List<NeoUsage> parents = new ArrayList<>();
    int idx = 0;
    for (RankedUsage rn : store.parents(child)) {
      NeoUsage u = store.usageWithName(rn.usageNode);
      parents.add(u);
      if (parentIdsToVerify != null) {
        assertEquals(u.getId(), parentIdsToVerify[idx]);
        idx++;
      }
    }
    if (parentIdsToVerify != null) {
      assertEquals(parents.size(), parentIdsToVerify.length);
    }
    return parents;
  }
  
  public Set<NeoUsage> synonyms(Node accepted, String... synonymNameIdsToVerify) {
    Set<NeoUsage> synonyms = new HashSet<>();
    for (Node sn : Traversals.SYNONYMS.traverse(accepted).nodes()) {
      synonyms.add(Preconditions.checkNotNull(store.usageWithName(sn)));
    }
    if (synonymNameIdsToVerify != null) {
      Set<String> ids = new HashSet<>();
      ids.addAll(Arrays.asList(synonymNameIdsToVerify));
      
      assertEquals(ids.size(), synonyms.size());
      for (NeoUsage s : synonyms) {
        assertTrue(ids.contains(s.usage.getName().getId()));
      }
    }
    return synonyms;
  }

  public boolean hasIssues(VerbatimEntity ent, Issue... issues) {
    IssueContainer ic = store.getVerbatim(ent.getVerbatimKey());
    for (Issue is : issues) {
      if (!ic.hasIssue(is))
        return false;
    }
    return true;
  }
  
  public NeoUsage usageByNameID(String id) {
    List<Node> usages = store.usageNodesByName(store.names().nodeByID(id));
    if (usages.size() != 1) {
      fail("No single usage for name " + id);
    }
    return store.usageWithName(usages.get(0));
  }

  public NeoUsage usageByID(String id) {
    return store.usageWithName(store.usages().nodeByID(id));
  }
  
  public NeoName nameByID(String id) {
    return store.names().objByID(id);
  }

  public void debug() throws Exception {
    store.dump(new File("graphs/debugtree.dot"));
  }
  
}
