package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.UnparsableException;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameValidator;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TxtTreeDao {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeDao.class);
  private final SqlSessionFactory factory;
  private static final int INDEX_BATCH_SIZE = 1000;
  private final NameUsageIndexService indexService;
  private final TaxonDao tdao;
  private final SynonymDao sdao;

  public TxtTreeDao(SqlSessionFactory factory, TaxonDao tdao, SynonymDao sdao, NameUsageIndexService indexService) {
    this.factory = factory;
    this.indexService = indexService;
    this.tdao = tdao;
    this.sdao = sdao;
  }

  public void readTxtree(int datasetKey, String id, OutputStream os) throws IOException {
    var ttp = TreeTraversalParameter.dataset(datasetKey);
    ttp.setTaxonID(id);
    ttp.setSynonyms(true);


    Writer writer = UTF8IoUtils.writerFromStream(os);
    TextTreePrinter printer = PrinterFactory.dataset(TextTreePrinter.class, ttp, null, null, null, null, factory, writer);
    printer.print();
    writer.flush();
  }

  private static NomCode code(NameUsage u) {
    return u == null ? null : (u.getName() == null ? null : u.getName().getCode());
  }

  public int insertTxtree(int datasetKey, String id, User user, InputStream txtree, boolean replace) throws IOException {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin != DatasetOrigin.PROJECT) {
      throw new IllegalArgumentException("Text trees can only be inserted into projects.");
    }

    final var key = DSID.of(datasetKey, id);
    Taxon parent; // must exist!
    Taxon grandparent;
    LinkedList<SimpleName> classification;
    try (SqlSession session = factory.openSession(true)) {
      var tm = session.getMapper(TaxonMapper.class);
      parent = tm.get(key);
      if (parent == null) {
        throw NotFoundException.notFound(Taxon.class, key);
      }
      grandparent = parent.getParentId() == null ? null : tm.get(key.id(parent.getParentId()));
      classification = new LinkedList<>(tm.classificationSimple(key));
      Collections.reverse(classification); // we need to start with highest rank down to lowest, incl the taxon itself
      classification.addLast(new SimpleName(parent));
    }
    // propagate the existing code to all inserted names
    final NomCode code = ObjectUtils.coalesce(code(parent), code(grandparent));
    final var tree = Tree.simple(txtree);

    if (replace) {
      // replace depends on tree content:
      // if single root and the name incl authorship is the same, replace the root and all its children
      // otherwise replace only the children
      boolean keepRoot = grandparent == null || tree.getRoot().size() != 1 || !tree.getRoot().get(0).name.equalsIgnoreCase(parent.getLabel());
      tdao.deleteRecursively(parent, keepRoot, user);
      if (!keepRoot) {
        // we must select a new parent as we just deleted the current one
        parent = grandparent;
      }
    }

    LOG.info("Insert tree with {} nodes by {} under parent {} ", tree.size(), user, parent);
    int counter = 0;
    var docs = new ArrayList<NameUsageWrapper>();
    for (SimpleTreeNode t : tree.getRoot()) {
      counter = counter + insertTaxon(parent, t, classification, code, user, docs);
    }
    // push remaining docs to ES
    if (docs.size() >= INDEX_BATCH_SIZE) {
      indexService.add(docs);
    }

    return counter;
  }

  private void addDoc(List<NameUsageWrapper> docs, NameUsageBase nu, LinkedList<SimpleName> classification) {
    var nuw = new NameUsageWrapper(nu);

    classification.addLast(new SimpleName(nu));
    nuw.setClassification(List.copyOf(classification));

    IssueContainer issues = IssueContainer.simple();
    NameValidator.flagIssues(nu.getName(), issues);
    nuw.setIssues(issues.getIssues());

    docs.add(nuw);
    if (docs.size() >= INDEX_BATCH_SIZE) {
      indexService.add(docs);
      docs.clear();
    }
  }

  private int insertTaxon(Taxon parent, SimpleTreeNode t, LinkedList<SimpleName> classification, NomCode code, User user, List<NameUsageWrapper> docs) {
    int counter = 0;
    final Name n = treeNode2name(parent.getDatasetKey(), t, code);
    final Taxon tax = new Taxon(n);
    tax.setParentId(parent.getId());
    tdao.create(tax, user.getKey(), false); // this also does name matching
    addDoc(docs, tax, classification);
    counter++;

    // synonyms
    for (SimpleTreeNode st : t.synonyms){
      final Name sn = treeNode2name(parent.getDatasetKey(), st, code);
      final Synonym syn = new Synonym(sn);
      syn.setAccepted(tax);
      sdao.create(syn, user.getKey());
      if (st.basionym) {
        try (SqlSession session = factory.openSession(true)) {
          var nrm = session.getMapper(NameRelationMapper.class);
          var rel = new NameRelation();
          rel.setDatasetKey(parent.getDatasetKey());
          rel.setNameId(n.getId());
          rel.setType(NomRelType.BASIONYM);
          rel.setRelatedNameId(sn.getId());
          rel.applyUser(user);
          nrm.create(rel);
        }
      }
      addDoc(docs, syn, classification);
      classification.removeLast(); // addDoc adds the synonym to the classification - we dont want this in the other usages
      counter++;
    }

    // accepted children
    for (SimpleTreeNode c : t.children){
      counter = counter + insertTaxon(tax, c, classification, code, user, docs);
    }

    // remove taxon from classification again
    classification.removeLast();

    return counter;
  }

  private static Name treeNode2name(int datasetKey, SimpleTreeNode tn, NomCode code) {
    Name n = new Name();
    n.setDatasetKey(datasetKey);
    n.setScientificName(tn.name);
    n.setCode(code);
    Rank rank = Rank.UNRANKED; // default for unknown
    try {
      var parsedRank = RankParser.PARSER.parse(code, tn.rank);
      if (parsedRank.isPresent()) {
        rank = parsedRank.get();
      }
    } catch (UnparsableException e) {
      rank = Rank.OTHER;
    }
    n.setRank(rank);
    return n;
  }

}
