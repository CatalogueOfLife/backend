package life.catalogue.importer.txttree;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.common.io.PathUtils;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.MappingInfos;
import life.catalogue.csv.SourceInvalidException;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.bibtex.BibTexInserter;
import life.catalogue.importer.csljson.CslJsonInserter;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.metadata.MetadataFactory;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;
import org.gbif.txtree.TreeLine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import javax.annotation.Nullable;

import static life.catalogue.parser.SafeParser.parse;

/**
 *
 */
public class TxtTreeInserter implements NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeInserter.class);
  private static final Pattern VERNACULAR = Pattern.compile("([a-z]{2,3}):(.+)");
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
  private final NomCode code;

  public TxtTreeInserter(NeoDb store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    if (!Files.isDirectory(folder)) {
      throw new FileNotFoundException("Folder does not exist: " + folder);
    }
    this.datasetKey = store.getDatasetKey();
    this.settings = settings;
    this.code = settings.getEnum(Setting.NOMENCLATURAL_CODE);
    this.store = store;
    this.folder = folder;
    findReadable(folder).ifPresent(f -> {
        treeFile = f;
        treeFileName = PathUtils.getFilename(f);
    });
    if (treeFile == null) {
      throw new SourceInvalidException("No valid tree data file found in " + folder);
    }
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
        if (!f.getFileName().toString().startsWith("expected") && Tree.verify(Files.newInputStream(f))) {
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
      if (bibIns != null) {
        LOG.info("Insert BibTex references");
        bibIns.insertAll();
      }
      LOG.info("Read tree and insert verbatim records from {}", treeFile);
      tree = org.gbif.txtree.Tree.simple(Files.newInputStream(treeFile), this::addVerbatim);
      LOG.info("Read tree with {} nodes from {}", tree.size(), treeFile);
      // insert names and taxa in depth first order
      int ordinal = 1;
      for (SimpleTreeNode t : tree.getRoot()) {
        try (Transaction tx = store.getNeo().beginTx()) {
          recursiveNodeInsert(null, t, ordinal++);
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

  private void recursiveNodeInsert(Node parent, SimpleTreeNode t, int ordinal) throws InterruptedException {
    NeoUsage u = usage(t, false, ordinal);
    store.createNameAndUsage(u);
    if (parent != null) {
      store.assignParent(parent, u.node);
    }
    for (SimpleTreeNode syn : t.synonyms){
      NeoUsage s = usage(syn, true, 0);
      store.createNameAndUsage(s);
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
      recursiveNodeInsert(u.node, c, childOrdinal++);
    }
  }

  private NeoUsage usage(SimpleTreeNode tn, boolean synonym, int ordinal) throws InterruptedException {
    VerbatimRecord v = store.getVerbatim(line2verbatimKey.get(tn.id));
    Rank rank = Rank.UNRANKED; // default for unknown
    try {
      var parsedRank = RankParser.PARSER.parse(code, tn.rank);
      if (parsedRank.isPresent()) {
        rank = parsedRank.get();
      }
    } catch (UnparsableException e) {
      v.addIssue(Issue.RANK_INVALID);
      rank = Rank.OTHER;
    }
    ParsedNameUsage nat = NameParser.PARSER.parse(tn.name, rank, null, v).get();
    NeoUsage u;
    if(synonym) {
      u = NeoUsage.createSynonym(Origin.SOURCE, nat.getName(), TaxonomicStatus.SYNONYM);
    } else {
      u = NeoUsage.createTaxon(Origin.SOURCE, nat.getName(), TaxonomicStatus.ACCEPTED);
      var t = u.asTaxon();
      t.setOrdinal(ordinal);
      // ENVIRONMENT
      if (hasDataItem(TxtTreeDataKey.ENV, tn)) {
        String[] vals = rmDataItem(TxtTreeDataKey.ENV, tn);
        for (String val : vals) {
          Environment env = parse(EnvironmentParser.PARSER, val).orNull(Issue.ENVIRONMENT_INVALID, v);
          if (env != null) {
            t.getEnvironments().add(env);
          }
        }
      }
      // CHRONO TEMPORAL RANGE
      if (hasDataItem(TxtTreeDataKey.CHRONO, tn)) {
        String[] vals = rmDataItem(TxtTreeDataKey.CHRONO, tn);
        for (String val : vals) {
          var range = val.split("-");
          if (range.length==1) {
            t.setTemporalRangeStart(range[0].trim());
            t.setTemporalRangeEnd(range[0].trim());
          } else if (range.length==2) {
            t.setTemporalRangeStart(range[0].trim());
            t.setTemporalRangeEnd(range[1].trim());
          } else {
            v.addIssue(Issue.GEOTIME_INVALID);
          }
        }
      }
      // TAX REF
      if (hasDataItem(TxtTreeDataKey.REF, tn)) {
        String[] vals = rmDataItem(TxtTreeDataKey.REF, tn);
        for (String val : vals) {
          if (referenceExists(val)) {
            t.addReferenceId(val);
          } else {
            v.addIssue(Issue.REFERENCE_ID_INVALID);
          }
        }
      }
      // Vernacular
      if (hasDataItem(TxtTreeDataKey.VERN, tn)) {
        String[] vals = rmDataItem(TxtTreeDataKey.VERN, tn);
        for (String val : vals) {
          var m = VERNACULAR.matcher(val);
          if (m.find()) {
            VernacularName vn = new VernacularName();
            var lang = parse(LanguageParser.PARSER, m.group(1)).orNull(Issue.VERNACULAR_LANGUAGE_INVALID, v);
            vn.setLanguage(lang);
            vn.setName(m.group(2));
            u.vernacularNames.add(vn);
          } else {
            v.addIssue(Issue.VERNACULAR_NAME_INVALID);
          }
        }
      }
      // ALL OTHER
      for (var entry : tn.infos.entrySet()) {
        // ignore the PUB entry which we handle below
        if (!entry.getKey().equalsIgnoreCase(TxtTreeDataKey.PUB.name())) {
          var tp = new TaxonProperty();
          if (entry.getValue() == null || entry.getValue().length == 0) continue;
          tp.setProperty(entry.getKey().toLowerCase());
          tp.setValue(String.join(", ", entry.getValue()));
          u.properties.add(tp);
        }
      }
    }
    u.setId(String.valueOf(tn.id));
    u.setVerbatimKey(v.getId());
    u.usage.setAccordingToId(nat.getTaxonomicNote());
    // NAME PUB REF
    if (hasDataItem(TxtTreeDataKey.PUB, tn)) {
      String[] vals = rmDataItem(TxtTreeDataKey.PUB, tn);
      for (String val : vals) {
        if (!referenceExists(val)) {
          v.addIssue(Issue.REFERENCE_ID_INVALID);
        } else if (u.usage.getName().getPublishedInId() != null){
          v.addIssue(Issue.MULTIPLE_PUBLISHED_IN_REFERENCES);
        } else {
          u.usage.getName().setPublishedInId(val);
        }
      }
    }
    // COMMENT
    u.usage.setRemarks(tn.comment);
    return u;
  }

  private boolean referenceExists(String id) {
    return refFactory.find(id) != null;
  }
  private boolean hasDataItem(TxtTreeDataKey key, SimpleTreeNode tn) {
    return tn.infos != null && tn.infos.containsKey(key.name());
  }
  private String[] rmDataItem(TxtTreeDataKey key, SimpleTreeNode tn) {
    return tn.infos.remove(key.name());
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
