package life.catalogue.db;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.txtree.Tree;
import life.catalogue.api.txtree.TreeNode;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.db.mapper.*;
import life.catalogue.parser.NameParser;
import life.catalogue.postgres.PgCopyUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NameType;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A junit test rule that loads test data from a text tree file into a given dataset.
 * <p>
 * The rule was designed to run as a junit {@link org.junit.Rule} before every test.
 * <p>
 * Unless an explicit factory is given, this rule requires a connected postgres server with mybatis via the {@link PgSetupRule}.
 * Make sure its setup!
 */
public class TxtTreeDataRule extends ExternalResource implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(TxtTreeDataRule.class);

  final private Map<Integer, TreeData> datasets;
  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;
  private NameMapper nm;
  private TaxonMapper tm;
  private SynonymMapper sm;

  public enum TreeData {
    ANIMALIA, MAMMALIA, TRILOBITA;

    InputStream resource() throws IOException {
      return Resources.getResourceAsStream("trees/" + name().toLowerCase()+".tree");
    }
  }


  public TxtTreeDataRule(Integer datasetKey, TreeData treeData) {
    this(Map.of(datasetKey, treeData));
  }

  public TxtTreeDataRule(Map<Integer, TreeData> treeData) {
    this.datasets = treeData;
    sqlSessionFactorySupplier = PgSetupRule::getSqlSessionFactory;
  }

  public <T> T getMapper(Class<T> mapperClazz) {
    return session.getMapper(mapperClazz);
  }

  public void commit() {
    session.commit();
  }

  public SqlSession getSqlSession() {
    return session;
  }

  @Override
  protected void before() throws Throwable {
    System.out.println("Load text trees");
    super.before();
    initSession();
    for (Map.Entry<Integer, TreeData> x : datasets.entrySet()) {
      final TreeData tree = x.getValue();
      final int datasetKey = x.getKey();
      LOG.info("Loading dataset {} from tree {}", datasetKey, tree);
      // create required partitions to load data
      PgSetupRule.partition(datasetKey);
      createDataset(datasetKey);
      loadTree(datasetKey, tree);
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
      dm.createWithKey(d);
    }
  }

  private void loadTree(int datasetKey, TreeData td) throws IOException {
    Tree tree = Tree.read(td.resource());
    LOG.debug("Inserting {} usages for dataset {}", tree.getCount(), datasetKey);
    for (TreeNode n : tree.getRoot().children) {
      insertSubtree(datasetKey, null, n);
    }
  }

  private void insertSubtree(int datasetKey, TreeNode parent, TreeNode t) {
    insertNode(datasetKey, parent, t, false);
    for (TreeNode syn : t.synonyms) {
      insertNode(datasetKey, t, syn, true);
    }
    for (TreeNode c : t.children) {
      insertSubtree(datasetKey, t, c);
    }
  }

  private void insertNode(int datasetKey, TreeNode parent, TreeNode tn, boolean synonym) {
    NameAccordingTo nat = NameParser.PARSER.parse(tn.name, tn.rank, null, IssueContainer.VOID).get();
    Name n = nat.getName();
    n.setDatasetKey(datasetKey);
    n.setId(String.valueOf(tn.id));
    n.setOrigin(Origin.SOURCE);
    n.applyUser(Users.DB_INIT);
    nm.create(n);

    if (synonym) {
      Synonym s = new Synonym();
      prepUsage(s, datasetKey, nat, TaxonomicStatus.SYNONYM, parent);
      sm.create(s);
    } else {
      Taxon t = new Taxon();
      prepUsage(t, datasetKey, nat, TaxonomicStatus.ACCEPTED, parent);
      tm.create(t);
    }
  }

  private static void prepUsage(NameUsageBase u, int datasetKey, NameAccordingTo nat, TaxonomicStatus status, TreeNode parent) {
      u.setDatasetKey(datasetKey);
      u.setId(String.valueOf(nat.getName().getId()));
      u.setName(nat.getName());
      u.setOrigin(Origin.SOURCE);
      u.applyUser(Users.DB_INIT);
      u.setStatus(status);
      u.setAccordingTo(nat.getAccordingTo());
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
    }
  }

  public void updateSequences(int datasetKey) {
    DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    pm.updateIdSequences(datasetKey);
    session.commit();
  }

}
