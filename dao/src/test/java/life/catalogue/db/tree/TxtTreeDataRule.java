package life.catalogue.db.tree;

import com.google.common.base.Preconditions;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.mapper.*;
import life.catalogue.parser.NameParser;

import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A junit test rule that loads test data from a text tree file into a given dataset.
 * <p>
 * The rule was designed to run as a junit {@link org.junit.Rule} before every test or test class if you only need to test reads.
 * <p>
 * Unless an explicit factory is given, this rule requires a connected postgres server with mybatis via the {@link PgSetupRule}.
 * Make sure its setup!
 */
public class TxtTreeDataRule extends ExternalResource implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeDataRule.class);

  final private List<TreeDataset> datasets;
  final private Set<Integer> sectors = new HashSet<>();
  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;
  private NameMapper nm;
  private TaxonMapper tm;
  private SynonymMapper sm;
  private ReferenceMapper rm;
  private SectorMapper secm;
  private AtomicInteger refID = new AtomicInteger(0);

  public enum TreeData {
    ANIMALIA, MAMMALIA, TRILOBITA;

    public String resource() {
      return "trees/" + name().toLowerCase()+".tree";
    }
  }

  public TxtTreeDataRule(Integer datasetKey, String treeResource) {
    this(List.of(new TreeDataset(datasetKey, treeResource)));
  }

  public TxtTreeDataRule(List<TreeDataset> treeData) {
    this.datasets = treeData;
    sqlSessionFactorySupplier = SqlSessionFactoryRule::getSqlSessionFactory;
  }

  public static class TreeDataset {
    public final Integer key;
    public final String resource;
    public final String title;
    public final DatasetOrigin origin;
    public final DatasetType type;

    public TreeDataset(Integer key, String resource, String title, DatasetOrigin origin, DatasetType type) {
      this.key = key;
      this.resource = Preconditions.checkNotNull(resource);
      this.title = title;
      this.origin = Preconditions.checkNotNull(origin);
      this.type = Preconditions.checkNotNull(type);
    }
    public TreeDataset(Integer key, String resource) {
      this (key, resource, resource, DatasetOrigin.PROJECT, DatasetType.TAXONOMIC);
    }
    public TreeDataset(Integer key, String resource, String title) {
      this (key, resource, title, DatasetOrigin.PROJECT, DatasetType.TAXONOMIC);
    }
    public TreeDataset(Integer key, String resource, String title, DatasetOrigin origin) {
      this (key, resource, title, origin, DatasetType.TAXONOMIC);
    }
    public TreeDataset(Integer key, String resource, String title, DatasetType type) {
      this (key, resource, title, DatasetOrigin.PROJECT, type);
    }
  }

  public static TxtTreeDataRule create(Map<Integer, TreeData> treeData) {
    var data = new ArrayList<TreeDataset>();
    for (Map.Entry<Integer, TreeData> x : treeData.entrySet()) {
      data.add(new TreeDataset(x.getKey(), x.getValue().resource(), x.getValue().name()));
    }
    return new TxtTreeDataRule(data);
  }

  @Override
  public void before() throws Throwable {
    LOG.info("Load text trees");
    super.before();
    refID.set(1);
    initSession();
    for (TreeDataset x : datasets) {
      LOG.info("Loading dataset {} from tree {}", x.key, x.resource);
      createDataset(x);
      loadTree(x);
      createSequences(x);
    }
  }

  private void createDataset(TreeDataset td) {
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    Dataset d = dm.get(td.key);
    if (d == null) {
      d = TestEntityGenerator.newDataset("Tree " + td.resource);
      d.setKey(td.key);
      d.applyUser(Users.TESTER);
      d.setOrigin(td.origin);
      d.setType(td.type);
      dm.createWithID(d);
    }
    // create sequences
    if (d.getOrigin() == DatasetOrigin.PROJECT) {
      session.getMapper(DatasetPartitionMapper.class).createSequences(d.getKey());
    }
    session.commit();
  }

  private void loadTree(TreeDataset src) throws IOException, InterruptedException{
    var stream = Resources.getResourceAsStream(src.resource);
    Tree<SimpleTreeNode> tree = Tree.simple(stream);
    LOG.debug("Inserting {} usages for dataset {}", tree.size(), src.key);
    for (SimpleTreeNode n : tree.getRoot()) {
      insertSubtree(src, null, n);
    }
  }

  private void insertSubtree(TreeDataset src, SimpleTreeNode parent, SimpleTreeNode t) throws InterruptedException {
    insertNode(src, parent, t, false);
    for (SimpleTreeNode syn : t.synonyms) {
      insertNode(src, t, syn, true);
    }
    for (SimpleTreeNode c : t.children) {
      insertSubtree(src, t, c);
    }
  }

  private void insertNode(TreeDataset src, SimpleTreeNode parent, SimpleTreeNode tn, boolean synonym) throws InterruptedException {
    ParsedNameUsage nat = NameParser.PARSER.parse(tn.name, tn.rank, null, VerbatimRecord.VOID).get();
    Name n = nat.getName();
    n.setDatasetKey(src.key);
    n.setId(String.valueOf(tn.id));
    n.setOrigin(Origin.SOURCE);
    n.applyUser(Users.DB_INIT);
    nm.create(n);

    Integer sk = null;
    if (src.origin.isManagedOrRelease() && tn.infos.containsKey(TxtTreeDataKey.PRIO.name())) {
      sk = Integer.parseInt(tn.infos.get(TxtTreeDataKey.PRIO.name())[0]);
      if (!sectors.contains(sk)) {
        Sector s = new Sector();
        s.setDatasetKey(src.key);
        s.setSubjectDatasetKey(src.key);
        s.setId(sk);
        s.applyUser(Users.DB_INIT);
        secm.createWithID(s);
        sectors.add(sk);
      }
    }

    if (synonym) {
      Synonym s = new Synonym();
      prepUsage(s, src.key, sk, nat, TaxonomicStatus.SYNONYM, parent, tn);
      sm.create(s);
    } else {
      Taxon t = new Taxon();
      prepUsage(t, src.key, sk, nat, TaxonomicStatus.ACCEPTED, parent, tn);
      tm.create(t);
    }
  }

  private void prepUsage(NameUsageBase u, int datasetKey, Integer sectorKey, ParsedNameUsage nat, TaxonomicStatus status, SimpleTreeNode parent, SimpleTreeNode tn) {
      u.setDatasetKey(datasetKey);
      u.setSectorKey(sectorKey);
      u.setId(String.valueOf(nat.getName().getId()));
      u.setName(nat.getName());
      u.setOrigin(Origin.SOURCE);
      u.applyUser(Users.DB_INIT);
      u.setStatus(status);
      if (parent != null) {
        u.setParentId(String.valueOf(parent.id));
      }
      if (nat.getTaxonomicNote() != null) {
        Reference r = new Reference();
        r.setId(String.valueOf(refID.getAndIncrement()));
        r.setCitation(nat.getTaxonomicNote());
        r.setDatasetKey(datasetKey);
        r.setSectorKey(sectorKey);
        r.applyUser(Users.DB_INIT);
        rm.create(r);
        u.setAccordingToId(r.getId());
      }
  }

  @Override
  public void after() {
    super.after();
    session.close();
  }

  @Override
  public void close() {
    after();
  }

  public void initSession() {
    if (session == null) {
      session = sqlSessionFactorySupplier.get().openSession(false);
      nm = session.getMapper(NameMapper.class);
      tm = session.getMapper(TaxonMapper.class);
      sm = session.getMapper(SynonymMapper.class);
      secm = session.getMapper(SectorMapper.class);
      rm = session.getMapper(ReferenceMapper.class);
    }
  }

  public void createSequences(TreeDataset src) {
    DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    if (src.origin == DatasetOrigin.PROJECT) {
      pm.createSequences(src.key);
    }
    session.commit();
  }

}
