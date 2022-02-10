package life.catalogue.importer.txttree;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.txtree.Tree;
import life.catalogue.api.txtree.TreeLine;
import life.catalogue.api.txtree.TreeNode;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.TxtTreeTerm;
import life.catalogue.coldp.NameParser;
import life.catalogue.common.io.PathUtils;
import life.catalogue.csv.CsvReader;
import life.catalogue.importer.MappingFlags;
import life.catalogue.importer.MetadataFactory;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;

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

/**
 *
 */
public class TxtTreeInserter implements NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeInserter.class);
  private static final MappingFlags FLAGS = new MappingFlags();
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
  private Tree tree;
  private Long2IntMap line2verbatimKey = new Long2IntOpenHashMap();

  public TxtTreeInserter(NeoDb store, Path folder) throws IOException {
    if (!Files.isDirectory(folder)) {
      throw new FileNotFoundException("Folder does not exist: " + folder);
    }
    this.datasetKey = store.getDatasetKey();
    this.store = store;
    this.folder = folder;
    findReadable(folder).ifPresent(f -> {
        treeFile = f;
        treeFileName = PathUtils.getFilename(f);
    });
    if (treeFile == null) {
      throw new NormalizationFailedException.SourceInvalidException("No valid tree data file found in " + folder);
    }
  }

  public static Optional<Path> findReadable(Path folder) {
    try {
      for (Path f : PathUtils.listFiles(folder, Set.of("tree", "txt", "text", "archive"))) {
        if (Tree.verify(Files.newInputStream(f))) {
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
    VerbatimRecord v = new VerbatimRecord(datasetKey, tl.line, treeFileName, TxtTreeTerm.Tree);
    v.getTerms().put(TxtTreeTerm.indent, String.valueOf(tl.level));
    v.getTerms().put(TxtTreeTerm.content, tl.content);
    store.put(v);
    line2verbatimKey.put(tl.line, (int) v.getId());
  }

  @Override
  public void insertAll() throws NormalizationFailedException {
    try {
      LOG.info("Read tree and insert verbatim records from {}", treeFile);
      tree = Tree.read(Files.newInputStream(treeFile), this::addVerbatim);
      LOG.info("Read tree with {} nodes from {}", tree.getCount(), treeFile);
      // insert names and taxa in depth first order
      for (TreeNode t : tree.getRoot().children) {
        try (Transaction tx = store.getNeo().beginTx()) {
          recursiveNodeInsert(null, t);
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

  private void recursiveNodeInsert(Node parent, TreeNode t){
    NeoUsage u = usage(t, false);
    store.createNameAndUsage(u);
    if (parent != null) {
      store.assignParent(parent, u.node);
    }
    for (TreeNode syn : t.synonyms){
      NeoUsage s = usage(syn, true);
      store.createNameAndUsage(s);
      store.createSynonymRel(s.node, u.node);
      if (syn.basionym) {
        NeoRel rel = new NeoRel();
        rel.setType(RelType.HAS_BASIONYM);
        rel.setVerbatimKey(line2verbatimKey.get(syn.id));
        store.createNeoRel(u.nameNode, s.nameNode, rel);
      }
    }
    for (TreeNode c : t.children){
      recursiveNodeInsert(u.node, c);
    }
  }

  private NeoUsage usage(TreeNode tn, boolean synonym) {
    VerbatimRecord v = store.getVerbatim(line2verbatimKey.get(tn.id));
    ParsedNameUsage nat = NameParser.PARSER.parse(tn.name, tn.rank, null, v).get();
    NeoUsage u = synonym ?
        NeoUsage.createSynonym(Origin.SOURCE, nat.getName(), TaxonomicStatus.SYNONYM) :
        NeoUsage.createTaxon(Origin.SOURCE, nat.getName(), TaxonomicStatus.ACCEPTED);
    u.setId(String.valueOf(tn.id));
    u.setVerbatimKey(v.getId());
    u.usage.setAccordingToId(nat.getTaxonomicNote());
    return u;
  }

  @Override
  public void reportBadFks() {
    // nothing to report
  }

  @Override
  public MappingFlags getMappingFlags() {
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
