package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.NomRelType;
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

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.io.*;

public class TxtTreeDao {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeDao.class);
  private final SqlSessionFactory factory;
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
    try (SqlSession session = factory.openSession(true)) {
      parent = session.getMapper(TaxonMapper.class).get(key);
    }
    if (parent == null) {
      throw NotFoundException.notFound(Taxon.class, key);
    }

    LOG.info("Insert tree with {} nodes by {} under parent {} ", tree.size(), user, parent);
    int counter = 0;
    for (SimpleTreeNode t : tree.getRoot()) {
      counter = counter + insertTaxon(parent, t, user);
    }
    LOG.info("Index inserted tree under parent {} ", key);
    //TODO: implement the processer for subtrees
    //indexService.indexSubtree(key);
    return counter;
  }

  private int insertTaxon(Taxon parent, SimpleTreeNode t, User user) {
    int counter = 0;
    final Name n = tree2name(parent.getDatasetKey(), t);
    final Taxon tax = new Taxon(n);
    tax.setParentId(parent.getId());
    tdo.create(tax, user.getKey(), false);
    counter++;

    // synonyms
    for (SimpleTreeNode st : t.synonyms){
      final Name sn = tree2name(parent.getDatasetKey(), t);
      final Synonym syn = new Synonym(sn);
      sdo.create(syn, user.getKey());
      counter++;

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
    }

    // accepted children
    for (SimpleTreeNode c : t.children){
      counter = counter + insertTaxon(tax, c, user);
    }
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
