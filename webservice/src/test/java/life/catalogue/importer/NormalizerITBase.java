package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.csv.ExcelCsvExtractor;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.neo.NotUniqueRuntimeException;
import life.catalogue.importer.neo.model.*;
import life.catalogue.importer.neo.printer.PrinterUtils;
import life.catalogue.importer.neo.traverse.Traversals;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.metadata.coldp.ColdpMetadataParser;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.Validation;
import javax.validation.Validator;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.*;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.io.Files;

import static org.junit.Assert.*;

abstract class NormalizerITBase {
  
  protected NeoDb store;
  private int attempt;
  protected NormalizerConfig cfg;
  private final DataFormat format;
  private final Supplier<NameIndex> nameIndexSupplier;
  protected DatasetWithSettings dws;
  private int datasetKey;

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
    attempt = 1;
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      store.closeAndDelete();
    }
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  public void normalizeExcel(String filename, NomCode code) throws Exception {
    InputStream xls = getClass().getResourceAsStream("/xls/"+filename);
    Path dir = java.nio.file.Files.createTempDirectory("col-gsd");
    ExcelCsvExtractor.extract(xls, dir.toFile());
    var settings = new DatasetSettings();
    settings.put(Setting.NOMENCLATURAL_CODE, code);
    normalize(dir, settings);
  }

  public void normalize(int datasetKey) throws Exception {
    normalize(datasetKey, (DatasetSettings) null);
  }

  public void assertTree() throws Exception {
    InputStream tree = getClass().getResourceAsStream(resourceDir() + "/expected.tree");
    String expected = UTF8IoUtils.readString(tree).trim();
    String neotree = PrinterUtils.textTree(store.getNeo());
    assertEquals(expected, neotree);

    // check also bare names if file exists
    InputStream bareNamesFile = getClass().getResourceAsStream(resourceDir() + "/expected-barenames.txt");
    if (bareNamesFile != null) {
      expected = UTF8IoUtils.readString(bareNamesFile).trim();

      String bareNames;
      try (Transaction tx = store.getNeo().beginTx()) {
        bareNames = store.bareNameNodes()
                         .map(NeoProperties::getRankedName)
                         .map(RankedName::toString)
                         .sorted()
                         .collect(Collectors.joining("\n"));
      }
      assertEquals("Bare names not as expected", expected, bareNames);
    }
  }

  public static Optional<NomCode> readDatasetCode(String resourceDir) {
    URL metaUrl = NormalizerTreeIT.class.getResource(resourceDir + "/metadata.yaml");
    if (metaUrl != null) {
      try {
        Optional<DatasetWithSettings> meta = ColdpMetadataParser.readYAML(metaUrl.openStream());
        if (meta.isPresent()) {
          NomCode code = meta.get().getCode();
          System.out.println("Use code " + code);
          return Optional.ofNullable(code);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return Optional.empty();
  }

  /**
   * Normalizes an archive from the test resources
   * and checks its printed txt tree against the expected tree
   *
   */
  public void normalize(int datasetKey, @Nullable DatasetSettings settings) throws Exception {
    this.datasetKey = datasetKey;
    String resourceDir = resourceDir();
    URL url = getClass().getResource(resourceDir);
    var code = readDatasetCode(resourceDir).orElse(null);
    if (code != null && settings != null && !settings.has(Setting.NOMENCLATURAL_CODE)) {
      settings.put(Setting.NOMENCLATURAL_CODE, code);
    }
    normalize(Paths.get(url.toURI()), settings);
  }

  public void normalize(int datasetKey, NomCode code) throws Exception {
    var settings = new DatasetSettings();
    settings.put(Setting.NOMENCLATURAL_CODE, code);
    normalize(datasetKey, settings);
  }

  protected static String resourceDir(int datasetKey, DataFormat format) {
    return "/" + format.getFilename().toLowerCase().replaceAll("_", "-") + "/" + datasetKey;
  }

  protected String resourceDir() {
    return resourceDir(datasetKey,  format);
  }
  
  public void normalize(URI url) throws Exception {
    normalize(url, null);
  }

  public void normalize(URI url, @Nullable DatasetSettings settings) throws Exception {
    // download an decompress
    ExternalSourceUtil.consumeSource(url, p -> normalize(p, settings));
  }
  
  protected void normalize(Path arch) {
    normalize(arch, null);
  }
  
  protected void normalize(Path arch, @Nullable DatasetSettings settings) {
    try {
      store = NeoDbFactory.create(1, attempt, cfg);
      dws = new DatasetWithSettings();
      dws.setKey(store.getDatasetKey());
      if (settings != null) {
        dws.setSettings(settings);
      }
      dws.setDataFormat(format);
      dws.setNotes("INITIAL dws");
      Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      Normalizer norm = new Normalizer(dws, store, arch, nameIndexSupplier.get(), ImageService.passThru(), validator, null);
      norm.call();
    
      // reopen
      store = NeoDbFactory.open(1, attempt,  cfg);
    
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);

    } finally {
      attempt++;
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

  public Reference accordingTo(NameUsage nu) {
    if (nu.getAccordingToId() != null) {
      return store.references().get(nu.getAccordingToId());
    }
    return null;
  }
  
  public NeoUsage byName(String name) {
    return byName(name, null);
  }
  
  public NeoUsage byName(String name, @Nullable String author) {
    Set<Node> usageNodes = store.usagesByName(name, author, null, true);
    if (usageNodes.isEmpty()) {
      throw new NotFoundException();
    }
    if (usageNodes.size() > 1) {
      throw new NotUniqueRuntimeException("scientificName", name);
    }
    return store.usageWithName(usageNodes.iterator().next());
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
  
  public NeoName assertBasionym(NeoUsage usage, @Nullable String basionymNameId) {
    NeoName nn = null;
    Relationship rel = usage.nameNode.getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING);
    if (basionymNameId == null) {
      assertNull(rel);
    } else {
      Node bn = rel.getOtherNode(usage.nameNode);
      nn = store.names().objByNode(bn);
      assertNotNull(nn);
      assertEquals(basionymNameId, nn.getName().getId());
    }
    return nn;
  }

  public boolean hasIssues(VerbatimEntity ent, Issue... issues) {
    VerbatimRecord ic = store.getVerbatim(ent.getVerbatimKey());
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
  
  public NeoUsage usageByName(Rank rank, String name) {
    Set<Node> usages = store.usagesByName(name, null, rank, true);
    if (usages.size()!=1) {
      throw new IllegalStateException(usages.size() + " usage nodes matching " + rank + " " + name);
    }
    return store.usageWithName(usages.iterator().next());
  }

  public NeoName nameByID(String id) {
    return store.names().objByID(id);
  }

  public Reference refByID(String id) {
    return store.references().get(id);
  }

  public void debug() throws Exception {
    store.dump(new File("graphs/debugtree.dot"));
  }

  public void printTree() throws Exception {
    store.debug();
  }

  public void printRelations(Node n) throws Exception {
    n.getRelationships().forEach(rel -> {
      System.out.println(rel.getStartNodeId() + " --"+ rel.getType() + "-> " + rel.getEndNodeId());
    });
  }

}
