package life.catalogue.db.tree;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.TxtTreeDataKey;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.mapper.*;
import life.catalogue.parser.NameParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.Priorities.USER;

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

  final private Map<Integer, String> datasets;
  final private Set<Integer> sectors = new HashSet<>();
  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;
  private NameMapper nm;
  private TaxonMapper tm;
  private SynonymMapper sm;
  private SectorMapper secm;

  public enum TreeData {
    ANIMALIA, MAMMALIA, TRILOBITA;

    public String resource() {
      return "trees/" + name().toLowerCase()+".tree";
    }
  }

  public TxtTreeDataRule(Integer datasetKey, TreeData tree) {
    this(datasetKey, tree.resource());
  }

  public TxtTreeDataRule(Integer datasetKey, String treeResource) {
    this(Map.of(datasetKey, treeResource));
  }

  public TxtTreeDataRule(Map<Integer, String> treeData) {
    this.datasets = treeData;
    sqlSessionFactorySupplier = SqlSessionFactoryRule::getSqlSessionFactory;
  }

  public static TxtTreeDataRule create(Map<Integer, TreeData> treeData) {
    Map<Integer, String> data = new HashMap<>();
    for (Map.Entry<Integer, TreeData> x : treeData.entrySet()) {
      data.put(x.getKey(), x.getValue().resource());
    }
    return new TxtTreeDataRule(data);
  }

  @Override
  protected void before() throws Throwable {
    LOG.info("Load text trees");
    super.before();
    initSession();
    for (Map.Entry<Integer, String> x : datasets.entrySet()) {
      final String treeName = x.getValue();
      final int datasetKey = x.getKey();
      LOG.info("Loading dataset {} from tree {}", datasetKey, treeName);
      createDataset(datasetKey);
      // create required partitions to load data
      MybatisTestUtils.partition(session, datasetKey);
      loadTree(datasetKey, treeName);
      updateSequences(datasetKey);
    }
  }

  private void createDataset(int datasetKey) {
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    Dataset d = dm.get(datasetKey);
    if (d == null) {
      d = TestEntityGenerator.newDataset("Tree " + datasetKey);
      d.setKey(datasetKey);
      d.applyUser(Users.TESTER);
      dm.create(d);
    }
    session.commit();
  }

  private void loadTree(int datasetKey, String resourceName) throws IOException, InterruptedException{
    var stream = Resources.getResourceAsStream(resourceName);
    Tree<SimpleTreeNode> tree = Tree.simple(stream);
    LOG.debug("Inserting {} usages for dataset {}", tree.size(), datasetKey);
    for (SimpleTreeNode n : tree.getRoot()) {
      insertSubtree(datasetKey, null, n);
    }
  }

  private void insertSubtree(int datasetKey, SimpleTreeNode parent, SimpleTreeNode t) throws InterruptedException {
    insertNode(datasetKey, parent, t, false);
    for (SimpleTreeNode syn : t.synonyms) {
      insertNode(datasetKey, t, syn, true);
    }
    for (SimpleTreeNode c : t.children) {
      insertSubtree(datasetKey, t, c);
    }
  }

  private void insertNode(int datasetKey, SimpleTreeNode parent, SimpleTreeNode tn, boolean synonym) throws InterruptedException {
    ParsedNameUsage nat = NameParser.PARSER.parse(tn.name, tn.rank, null, VerbatimRecord.VOID).get();
    Name n = nat.getName();
    n.setDatasetKey(datasetKey);
    n.setId(String.valueOf(tn.id));
    n.setOrigin(Origin.SOURCE);
    n.applyUser(Users.DB_INIT);
    nm.create(n);

    Integer sk = null;
    if (tn.infos.containsKey(TxtTreeDataKey.PRIO.name())) {
      sk = Integer.parseInt(tn.infos.get(TxtTreeDataKey.PRIO.name())[0]);
      if (!sectors.contains(sk)) {
        Sector s = new Sector();
        s.setDatasetKey(datasetKey);
        s.setSubjectDatasetKey(datasetKey);
        s.setId(sk);
        s.applyUser(Users.DB_INIT);
        secm.createWithID(s);
        sectors.add(sk);
      }
    }

    if (synonym) {
      Synonym s = new Synonym();
      prepUsage(s, datasetKey, sk, nat, TaxonomicStatus.SYNONYM, parent, tn);
      sm.create(s);
    } else {
      Taxon t = new Taxon();
      prepUsage(t, datasetKey, sk, nat, TaxonomicStatus.ACCEPTED, parent, tn);
      tm.create(t);
    }
  }

  private static void prepUsage(NameUsageBase u, int datasetKey, Integer sectorKey, ParsedNameUsage nat, TaxonomicStatus status, SimpleTreeNode parent, SimpleTreeNode tn) {
      u.setDatasetKey(datasetKey);
      u.setSectorKey(sectorKey);
      u.setId(String.valueOf(nat.getName().getId()));
      u.setName(nat.getName());
      u.setOrigin(Origin.SOURCE);
      u.applyUser(Users.DB_INIT);
      u.setStatus(status);
      u.setAccordingToId(nat.getTaxonomicNote());
      if (parent != null) {
        u.setParentId(String.valueOf(parent.id));
      }
  }

  @Override
  protected void after() {
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
    }
  }

  public void updateSequences(int datasetKey) {
    DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    pm.updateIdSequences(datasetKey);
    pm.createManagedSequences(datasetKey);
    session.commit();
  }

}
