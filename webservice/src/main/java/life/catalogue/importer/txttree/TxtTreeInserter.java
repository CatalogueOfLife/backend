package life.catalogue.importer.txttree;

import life.catalogue.api.model.*;
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
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.metadata.MetadataFactory;
import life.catalogue.parser.*;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.TreeLine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import static life.catalogue.api.vocab.terms.TxtTreeTerm.*;
import static life.catalogue.importer.InterpreterBase.normGeoTime;
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
        if (!f.getFileName().toString().startsWith("expected") && org.gbif.txtree.Tree.verify(Files.newInputStream(f))) {
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

  private void recursiveNodeInsert(Node parent, SimpleTreeNode t, int ordinal, NomCode parentCode) throws InterruptedException {
    NeoUsage u = usage(t, false, ordinal, parentCode);
    final NomCode code = u.usage.getName().getCode();
    store.createNameAndUsage(u); // this removes the usage.name
    if (parent != null) {
      store.assignParent(parent, u.node);
    }
    for (SimpleTreeNode syn : t.synonyms){
      NeoUsage s = usage(syn, true, 0, code);
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
      recursiveNodeInsert(u.node, c, childOrdinal++, code);
    }
  }

  private NeoUsage usage(SimpleTreeNode tn, boolean synonym, int ordinal, NomCode parentCode) throws InterruptedException {
    VerbatimRecord v = store.getVerbatim(line2verbatimKey.get(tn.id));
    final int existingIssues = v.getIssues().size();

    // CODE is inherited from above
    final NomCode code = SafeParser.parse(NomCodeParser.PARSER, rmSingleDataItem(CODE, tn)).orElse(parentCode, Issue.NOMENCLATURAL_CODE_INVALID, v);

    // RANK
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

    // NAME
    ParsedNameUsage pnu = NameParser.PARSER.parse(tn.name, rank, code, v).get();
    // PUB REF
    if (hasDataItem(PUB, tn)) {
      String[] vals = rmDataItem(PUB, tn);
      for (String val : vals) {
        if (!referenceExists(val)) {
          v.addIssue(Issue.REFERENCE_ID_INVALID);
        } else if (pnu.getName().getPublishedInId() != null){
          v.addIssue(Issue.MULTIPLE_PUBLISHED_IN_REFERENCES);
        } else {
          pnu.getName().setPublishedInId(val);
        }
      }
    }

    // link (remove before we add properties)
    URI link = parse(UriParser.PARSER, rmSingleDataItem(LINK, tn)).orNull(Issue.URL_INVALID, v);

    // USAGE
    NeoUsage u;
    if(synonym) {
      u = NeoUsage.createSynonym(Origin.SOURCE, pnu.getName(), TaxonomicStatus.SYNONYM);
    } else {
      TaxonomicStatus status = pnu.isDoubtful() || rmBoolean(PROV, tn, v) ? TaxonomicStatus.PROVISIONALLY_ACCEPTED : TaxonomicStatus.ACCEPTED;
      u = NeoUsage.createTaxon(Origin.SOURCE, pnu.getName(), status);
      var t = u.asTaxon();
      t.setOrdinal(ordinal);
      // DAGGER
      t.setExtinct(tn.extinct);
      // ENVIRONMENT
      if (hasDataItem(ENV, tn)) {
        String[] vals = rmDataItem(ENV, tn);
        for (String val : vals) {
          Environment env = parse(EnvironmentParser.PARSER, val).orNull(Issue.ENVIRONMENT_INVALID, v);
          if (env != null) {
            t.getEnvironments().add(env);
          }
        }
      }
      // CHRONO TEMPORAL RANGE
      if (hasDataItem(CHRONO, tn)) {
        String[] vals = rmDataItem(CHRONO, tn);
        for (String val : vals) {
          var range = val.split("-");
          if (range.length==1) {
            t.setTemporalRangeStart(normGeoTime(range[0],v));
            t.setTemporalRangeEnd(normGeoTime(range[0],v));
          } else if (range.length==2) {
            t.setTemporalRangeStart(normGeoTime(range[0],v));
            t.setTemporalRangeEnd(normGeoTime(range[1],v));
          } else {
            v.addIssue(Issue.GEOTIME_INVALID);
          }
        }
      }
      // TAX REF
      if (hasDataItem(REF, tn)) {
        String[] vals = rmDataItem(REF, tn);
        for (String val : vals) {
          if (referenceExists(val)) {
            t.addReferenceId(val);
          } else {
            v.addIssue(Issue.REFERENCE_ID_INVALID);
          }
        }
      }
      // Vernacular
      if (hasDataItem(VERN, tn)) {
        String[] vals = rmDataItem(VERN, tn);
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
        if (!entry.getKey().equalsIgnoreCase(PUB.name())) {
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
    u.asNameUsageBase().setLink(link);
    u.usage.setAccordingToId(pnu.getTaxonomicNote());
    // COMMENT
    u.usage.setRemarks(tn.comment);
    // store issues?
    if (existingIssues < v.getIssues().size()) {
      store.put(v);
    }
    return u;
  }

  private boolean referenceExists(String id) {
    return refFactory.find(id) != null;
  }
  private boolean hasDataItem(TxtTreeTerm key, SimpleTreeNode tn) {
    return tn.infos != null && tn.infos.containsKey(key.name());
  }
  private String[] rmDataItem(TxtTreeTerm key, SimpleTreeNode tn) {
    return tn.infos.remove(key.name());
  }
  private String rmSingleDataItem(TxtTreeTerm key, SimpleTreeNode tn) {
    var vals = tn.infos.remove(key.name());
    if (vals == null || vals.length == 0) {
      return null;
    } else if (vals.length == 1) {
      return vals[0];
    } else {
      return String.join(",", vals);
    }
  }

  private boolean rmBoolean(TxtTreeTerm key, SimpleTreeNode tn, IssueContainer issues) {
    var val = rmSingleDataItem(key, tn);
    return SafeParser.parse(BooleanParser.PARSER, val).orElse(Boolean.FALSE, Issue.PROVISIONAL_STATUS_INVALID, issues);
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
