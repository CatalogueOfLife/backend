package life.catalogue.importer.txttree;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.io.PathUtils;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.MappingInfos;
import life.catalogue.csv.SourceInvalidException;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.bibtex.BibTexInserter;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.interpreter.TxtTreeInterpreter;
import life.catalogue.metadata.MetadataFactory;

import org.gbif.nameparser.api.NomCode;
import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.TreeLine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import static life.catalogue.api.vocab.terms.TxtTreeTerm.*;

public class TxtTreeInserter implements NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeInserter.class);
  private static final MappingInfos FLAGS = new MappingInfos();
  static {
    FLAGS.setDenormedClassificationMapped(false);
    FLAGS.setAcceptedNameMapped(false);
    FLAGS.setParentNameMapped(false);
    FLAGS.setOriginalNameMapped(false);
    FLAGS.setTaxonId(false);
  }
  private final Path folder;
  private final NeoDb store;
  private final int datasetKey;
  private Path treeFile;
  private String treeFileName;
  private org.gbif.txtree.Tree<SimpleTreeNode> tree;
  private Long2IntMap line2verbatimKey = new Long2IntOpenHashMap();
  private final BibTexInserter bibIns;
  private final ReferenceFactory refFactory;
  private final DatasetSettings settings;
  private final TxtTreeInterpreter interpreter;

  public TxtTreeInserter(NeoDb store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    if (!Files.isDirectory(folder)) {
      throw new FileNotFoundException("Folder does not exist: " + folder);
    }
    this.datasetKey = store.getDatasetKey();
    this.settings = settings;
    this.store = store;
    this.folder = folder;
    findReadable(folder).ifPresent(f -> {
        treeFile = f;
        treeFileName = PathUtils.getFilename(f);
    });
    if (treeFile == null) {
      throw new SourceInvalidException("No valid tree data file found in " + folder);
    }
    interpreter = new TxtTreeInterpreter();
    this.refFactory = refFactory;
    Path bib = folder.resolve("reference.bib");
    if (Files.exists(bib)) {
      LOG.info("BibTeX file found: {}", bib);
      bibIns = new BibTexInserter(store, bib.toFile(), refFactory);
    } else {
      bibIns = null;
    }
  }

  public static Optional<Path> findReadable(Path folder) {
    try {
      for (Path f : PathUtils.listFiles(folder, Set.of("txtree", "tree", "txt", "text", "archive"))) {
        if (!f.getFileName().toString().startsWith("expected") && org.gbif.txtree.Tree.verify(Files.newInputStream(f)).valid) {
          LOG.info("Found readable tree file {}", f);
          return Optional.of(f);
        }
      }
    } catch (Exception e) {
      LOG.warn("Error trying to find readable text tree in folder {}", folder, e);
    }
    return Optional.empty();
  }

  private void addVerbatim(TreeLine tl){
    VerbatimRecord v = new VerbatimRecord(datasetKey, tl.line, treeFileName, Tree);
    v.getTerms().put(indent, String.valueOf(tl.level));
    v.getTerms().put(content, tl.content);
    if (tl.infos != null) {
      for (var entry : tl.infos.entrySet()) {

      }
    }
    store.put(v);
    line2verbatimKey.put(tl.line, (int) v.getId());
  }

  @Override
  public void insertAll() throws NormalizationFailedException {
    try {
      if (bibIns != null) {
        LOG.info("Insert BibTex references");
        bibIns.insertAll();
      }
      LOG.info("Read tree and insert verbatim records from {}", treeFile);
      tree = org.gbif.txtree.Tree.simple(Files.newInputStream(treeFile), this::addVerbatim);
      LOG.info("Read tree with {} nodes from {}", tree.size(), treeFile);
      // insert names and taxa in depth first order
      int ordinal = 1;
      NomCode code = settings.getEnum(Setting.NOMENCLATURAL_CODE);
      for (SimpleTreeNode t : tree.getRoot()) {
        try (Transaction tx = store.getNeo().beginTx()) {
          recursiveNodeInsert(null, t, ordinal++, code);
          tx.success();
        }
      }
    } catch (Exception e) {
      throw new NormalizationFailedException("Failed to insert text tree from " + treeFile, e);
    }
  }

  @Override
  public Optional<DatasetWithSettings> readMetadata() {
    return MetadataFactory.readMetadata(folder);
  }

  private void persist(NeoUsage u, SimpleTreeNode t) {
    store.createNameAndUsage(u); // this removes the usage.name
    if (u.getId() == null) {
      // try again with line number as ID in case of duplicates
      u.setId(String.valueOf(t.id));
      store.usages().create(u);
    }
  }

  private void recursiveNodeInsert(Node parent, SimpleTreeNode t, int ordinal, NomCode parentCode) throws InterruptedException {
    NeoUsage u = usage(t, false, ordinal, parentCode);
    final NomCode code = u.usage.getName().getCode();
    persist(u, t);
    if (parent != null) {
      store.assignParent(parent, u.node);
    }
    for (SimpleTreeNode syn : t.synonyms){
      NeoUsage s = usage(syn, true, 0, code);
      persist(s, t);
      store.createSynonymRel(s.node, u.node);
      if (syn.basionym) {
        NeoRel rel = new NeoRel();
        rel.setType(RelType.HAS_BASIONYM);
        rel.setVerbatimKey(line2verbatimKey.get(syn.id));
        store.createNeoRel(u.nameNode, s.nameNode, rel);
      }
    }
    int childOrdinal = 1;
    for (SimpleTreeNode c : t.children){
      recursiveNodeInsert(u.node, c, childOrdinal++, code);
    }
  }


  private NeoUsage usage(SimpleTreeNode tn, boolean synonym, int ordinal, NomCode parentCode) throws InterruptedException {
    VerbatimRecord v = store.getVerbatim(line2verbatimKey.get(tn.id));
    final int existingIssues = v.getIssues().size();
    // convert
    var tu = interpreter.interpret(tn, synonym, ordinal, parentCode, this::referenceExists);
    tu.usage.setVerbatimKey(v.getId());
    // store issues?
    v.add(tu.issues.getIssues());
    if (existingIssues < v.getIssues().size()) {
      store.put(v);
    }
    return new NeoUsage(tu);
  }

  private boolean referenceExists(String id) {
    return refFactory.find(id) != null;
  }

  @Override
  public void reportBadFks() {
    // nothing to report
  }

  @Override
  public MappingInfos getMappingFlags() {
    return FLAGS;
  }

  public Optional<Path> logo() {
    Path logo = folder.resolve(CsvReader.LOGO_FILENAME);
    if (Files.exists(logo)) {
      return Optional.of(logo);
    }
    return Optional.empty();
  }
}
