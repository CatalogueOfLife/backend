package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.es.NameUsageIndexService;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TxtTreeDao {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeDao.class);
  private final SqlSessionFactory factory;
  private static final int INDEX_BATCH_SIZE = 1000;
  private final NameUsageIndexService indexService;
  private final TaxonDao tdo;
  private final SynonymDao sdo;

  public TxtTreeDao(SqlSessionFactory factory, TaxonDao tdo, SynonymDao sdo, NameUsageIndexService indexService) {
    this.factory = factory;
    this.indexService = indexService;
    this.tdo = tdo;
    this.sdo = sdo;
  }

  public void readTxtree(int datasetKey, String id, OutputStream os) throws IOException {
    var ttp = TreeTraversalParameter.dataset(datasetKey);
    ttp.setTaxonID(id);
    ttp.setSynonyms(true);

    Writer writer = new BufferedWriter(new OutputStreamWriter(os));
    TextTreePrinter printer = PrinterFactory.dataset(TextTreePrinter.class, ttp, null, null, null, factory, writer);
    printer.print();
    writer.flush();
  }

  public int insertTxtree(int datasetKey, String id, User user, InputStream txtree) throws IOException {
    var tree = Tree.simple(txtree);
    final var key = DSID.of(datasetKey, id);
    Taxon parent;
    LinkedList<SimpleName> classification;
    try (SqlSession session = factory.openSession(true)) {
      var tm = session.getMapper(TaxonMapper.class);
      parent = tm.get(key);
      if (parent == null) {
        throw NotFoundException.notFound(Taxon.class, key);
      }
      classification = new LinkedList<>(tm.classificationSimple(key));
      Collections.reverse(classification); // we need to start with highest rank down to lowest, incl the taxon itself
      classification.addLast(new SimpleName(parent));
    }

    LOG.info("Insert tree with {} nodes by {} under parent {} ", tree.size(), user, parent);
    int counter = 0;
    var docs = new ArrayList<NameUsageWrapper>();
    for (SimpleTreeNode t : tree.getRoot()) {
      counter = counter + insertTaxon(parent, t, classification, user, docs);
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
    docs.add(nuw);
    if (docs.size() >= INDEX_BATCH_SIZE) {
      indexService.add(docs);
      docs.clear();
    }
  }

  private int insertTaxon(Taxon parent, SimpleTreeNode t, LinkedList<SimpleName> classification, User user, List<NameUsageWrapper> docs) {
    int counter = 0;
    final Name n = tree2name(parent.getDatasetKey(), t);
    final Taxon tax = new Taxon(n);
    tax.setParentId(parent.getId());
    tdo.create(tax, user.getKey(), false);
    addDoc(docs, tax, classification);
    counter++;

    // synonyms
    for (SimpleTreeNode st : t.synonyms){
      final Name sn = tree2name(parent.getDatasetKey(), st);
      final Synonym syn = new Synonym(sn);
      sdo.create(syn, user.getKey());
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
      counter = counter + insertTaxon(tax, c, classification, user, docs);
    }

    // remove taxon from classification again
    classification.removeLast();

    return counter;
  }

  private static Name tree2name(int datasetKey, SimpleTreeNode tree) {
    Name n = new Name();
    n.setDatasetKey(datasetKey);
    n.setScientificName(tree.name);
    n.setRank(tree.rank);
    return n;
  }

}
