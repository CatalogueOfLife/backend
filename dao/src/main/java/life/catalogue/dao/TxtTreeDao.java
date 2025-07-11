package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameValidator;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.*;
import java.util.function.Predicate;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class TxtTreeDao {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeDao.class);
  private final SqlSessionFactory factory;
  private static final int INDEX_BATCH_SIZE = 1000;
  private final NameUsageIndexService indexService;
  private final TaxonDao tdao;
  private final SynonymDao sdao;
  private final TxTreeNodeInterpreter interpreter;

  public TxtTreeDao(SqlSessionFactory factory, TaxonDao tdao, SynonymDao sdao, NameUsageIndexService indexService, TxTreeNodeInterpreter interpreter) {
    this.factory = factory;
    this.indexService = indexService;
    this.tdao = tdao;
    this.sdao = sdao;
    this.interpreter = interpreter;
  }

  public void readTxtree(int datasetKey, String id, boolean extended, Set<Rank> ranks, OutputStream os) throws IOException {
    var ttp = TreeTraversalParameter.dataset(datasetKey);
    ttp.setTaxonID(id);
    ttp.setSynonyms(true);

    try (Writer writer = UTF8IoUtils.writerFromStream(os);
         TextTreePrinter printer = PrinterFactory.dataset(TextTreePrinter.class, ttp, ranks, null, null, null, factory, writer)
    ) {
      if (extended) {
        printer.showExtendedInfos();
      }
      printer.print();
      writer.flush();
    }
  }

  private static NomCode code(NameUsage u) {
    return u == null ? null : (u.getName() == null ? null : u.getName().getCode());
  }

  public int insertTxtree(int datasetKey, String id, User user, InputStream txtree, boolean replace) throws IOException, InterruptedException {
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
    var typeNameRelations = new ArrayList<NameRelation>();
    for (SimpleTreeNode t : tree.getRoot()) {
      counter += insertTaxon(parent, t, 0, classification, code, user, typeNameRelations, docs);
    }
    // finally we can lookup related name ids and insert the relations
    insertNameRelations(datasetKey, typeNameRelations, code, user);
    // push remaining docs to ES
    if (docs.size() >= INDEX_BATCH_SIZE) {
      indexService.add(docs);
    }

    return counter;
  }

  private void insertNameRelations(int datasetKey, ArrayList<NameRelation> typeNameRelations, NomCode code, User user) {
    try (SqlSession session = factory.openSession(false)) {
      var nrm = session.getMapper(NameRelationMapper.class);
      var nm = session.getMapper(NameMapper.class);
      for (NameRelation nr : typeNameRelations) {
        String name = nr.getRelatedNameId();
        Rank rank = name.contains(" ") ? Rank.SPECIES : Rank.GENUS;
        var results = nm.find(datasetKey, name, rank);
        if (results.isEmpty()) {
          // create new name
          //TODO: parse name
          Name n = new Name();
          n.setDatasetKey(datasetKey);
          n.setRank(rank);
          n.setScientificName(name);
          n.setType(NameType.SCIENTIFIC);
          n.setCode(code);
          n.applyUser(user);

          nm.create(n);
          nr.setRelatedNameId(n.getId());
        } else {
          nr.setRelatedNameId(results.get(0).getId());
          if (results.size() > 1) {
            LOG.warn("More than one name found for {} {} in dataset {}", rank, name, datasetKey);
          }
        }
        nr.setDatasetKey(datasetKey);
        nr.applyUser(user);
        nrm.create(nr);
      }
    }
  }

  private void addDoc(List<NameUsageWrapper> docs, NameUsageBase nu, LinkedList<SimpleName> classification, IssueContainer issues) {
    var nuw = new NameUsageWrapper(nu);

    classification.addLast(new SimpleName(nu));
    nuw.setClassification(List.copyOf(classification));

    NameValidator.flagIssues(nu.getName(), issues);
    nuw.setIssues(issues.getIssues());

    docs.add(nuw);
    if (docs.size() >= INDEX_BATCH_SIZE) {
      indexService.add(docs);
      docs.clear();
    }
  }

  private boolean referenceExists(DSID<String> rid) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(ReferenceMapper.class).exists(rid);
    }
  }
  private Predicate<String> refExistsFunc(int datasetKey) {
    return new Predicate<>() {
      @Override
      public boolean test(String s) {
        return referenceExists(DSID.of(datasetKey, s));
      }
    };
  }

  private static TxtUsage prep(int datasetKey, String parentID, TxtUsage nu) {
    nu.usage.setParentId(parentID);
    // dont reuse any ids but force inserts!
    nu.usage.setId(null);
    nu.usage.getName().setId(null);
    nu.usage.setDatasetKey(datasetKey);
    nu.usage.getName().setDatasetKey(datasetKey);
    return nu;
  }

  /**
   * Recursive insert of a txt tree node, inserting the taxon itself and then all synonyms and children
   *
   * @param parent
   * @param t
   * @param classification
   * @param code
   * @param user
   * @param typeNameRelations
   * @param docs
   * @return number of total inserts done
   */
  private int insertTaxon(Taxon parent, SimpleTreeNode t, int ordinal, LinkedList<SimpleName> classification, NomCode code, User user, ArrayList<NameRelation> typeNameRelations, List<NameUsageWrapper> docs) throws InterruptedException {
    int counter = 0;
    final int datasetKey = parent.getDatasetKey();
    var refExists = refExistsFunc(datasetKey);
    var tu = prep(datasetKey, parent.getId(), interpreter.interpret(t, false, ordinal, code, refExists));
    final Taxon tax = tu.usage.asTaxon();
    tdao.create(tax, user.getKey(), false); // this also does name matching
    addDoc(docs, tax, classification, tu.issues);
    counter++;
    // vernacular?
    if (tu.hasTaxonRelatedEntities()) {
      try (SqlSession session = factory.openSession(false)) {
        var vm = session.getMapper(VernacularNameMapper.class);
        var dm = session.getMapper(DistributionMapper.class);
        var pm = session.getMapper(TaxonPropertyMapper.class);
        var mm = session.getMapper(MediaMapper.class);
        var em = session.getMapper(EstimateMapper.class);
        for (var v : tu.vernacularNames) {
          fill(v, datasetKey, user);
          vm.create(v, tax.getId());
        }
        for (var d : tu.distributions) {
          fill(d, datasetKey, user);
          dm.create(d, tax.getId());
        }
        for (var p : tu.properties) {
          fill(p, datasetKey, user);
          pm.create(p, tax.getId());
        }
        for (var m : tu.media) {
          fill(m, datasetKey, user);
          mm.create(m, tax.getId());
        }
        for (var m : tu.estimates) {
          fill(m, datasetKey, user);
          m.setTarget(SimpleNameLink.of(tax.getId(), tax.getName().getScientificName(), tax.getName().getAuthorship(), tax.getName().getRank()));
          em.create(m);
        }
        session.commit();
      }
      // type and type material
      addNameRelated(tu, tax.getName(), typeNameRelations, datasetKey, user);
    }

    // synonyms
    for (SimpleTreeNode st : t.synonyms){
      var su = prep(datasetKey, parent.getId(), interpreter.interpret(st, true, 0, code, refExists));
      var syn = su.usage.asSynonym();
      syn.setAccepted(tax);
      sdao.create(syn, user.getKey());
      if (st.basionym) {
        try (SqlSession session = factory.openSession(true)) {
          var nrm = session.getMapper(NameRelationMapper.class);
          var rel = new NameRelation();
          rel.setDatasetKey(parent.getDatasetKey());
          rel.setNameId(tax.getName().getId());
          rel.setType(NomRelType.BASIONYM);
          rel.setRelatedNameId(syn.getName().getId());
          rel.applyUser(user);
          nrm.create(rel);
        }
      }
      // type and type material
      addNameRelated(su, syn.getName(), typeNameRelations, datasetKey, user);
      addDoc(docs, syn, classification, su.issues);
      classification.removeLast(); // addDoc adds the synonym to the classification - we dont want this in the other usages
      counter++;
    }

    // accepted children
    int childOrd = 1;
    for (SimpleTreeNode c : t.children){
      counter += insertTaxon(tax, c, childOrd++, classification, code, user, typeNameRelations, docs);
    }

    // remove taxon from classification again
    classification.removeLast();

    return counter;
  }

  private void addNameRelated(TxtUsage tu, Name n, ArrayList<NameRelation> typeNameRelations, int datasetKey, User user) {
    if (!tu.typeMaterial.isEmpty()) {
      try (SqlSession session = factory.openSession(true)) {
        var tm = session.getMapper(TypeMaterialMapper.class);
        for (var m : tu.typeMaterial) {
          fill(m, datasetKey, user);
          m.setNameId(n.getId());
          tm.create(m);
        }
      }
    }
    if (StringUtils.isNotBlank(tu.type)) {
      var rel = new NameRelation();
      rel.setDatasetKey(datasetKey);
      rel.setNameId(n.getId());
      rel.setType(NomRelType.TYPE);
      rel.applyUser(user);
      // find related id via name later
      rel.setRelatedNameId(tu.type);
      typeNameRelations.add(rel);
    }
  }
  private <T extends DatasetScopedEntity<?>> T fill(T obj, int datasetKey, User user) {
    obj.setDatasetKey(datasetKey);
    obj.setModifiedBy(user.getKey());
    obj.setCreatedBy(user.getKey());
    return obj;
  }

  public static class TxtUsage {
    public NameUsageBase usage;
    public final IssueContainer issues = new IssueContainer.Simple();
    public final List<Distribution> distributions = Lists.newArrayList();
    public final List<Media> media = Lists.newArrayList();
    public final List<VernacularName> vernacularNames = Lists.newArrayList();
    public final List<SpeciesEstimate> estimates = Lists.newArrayList();
    public final List<TaxonProperty> properties = Lists.newArrayList();
    public String type;
    public final List<TypeMaterial> typeMaterial = Lists.newArrayList();
    boolean hasTaxonRelatedEntities() {
      return !distributions.isEmpty() || !media.isEmpty() || !vernacularNames.isEmpty() || !estimates.isEmpty() || !properties.isEmpty();
    }
  }

  public interface TxTreeNodeInterpreter {
    TxtUsage interpret(SimpleTreeNode tn, boolean synonym, int ordinal, NomCode parentCode, Predicate<String> referenceExists) throws InterruptedException;
  }

}
