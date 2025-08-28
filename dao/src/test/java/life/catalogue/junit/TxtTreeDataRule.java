package life.catalogue.junit;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.common.csl.CslDataConverter;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.CopyUtil;
import life.catalogue.dao.TaxonMetricsBuilder;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.db.mapper.*;
import life.catalogue.parser.NameParser;
import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jbibtex.BibTeXDatabase;
import org.jbibtex.ParseException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import de.undercouch.citeproc.bibtex.BibTeXConverter;

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
  private static final String KEY_PRIO = "PRIO";

  final private boolean keepOrder;
  final private boolean buildMetrics;
  final private List<TreeDataset> datasets;
  final private Set<Integer> sectors = new HashSet<>();
  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;
  private NameMapper nm;
  private TaxonMapper tm;
  private SynonymMapper sm;
  private ReferenceMapper rm;
  private SectorMapper secm;
  private VernacularNameMapper vm;
  private AtomicInteger refID = new AtomicInteger(0);
  private Map<Long, String> usageIDs = new HashMap<>();

  public enum TreeData {
    ANIMALIA, MAMMALIA, TRILOBITA, AVES;

    public String resource() {
      return "trees/" + name().toLowerCase()+".tree";
    }
  }

  public TxtTreeDataRule(Integer datasetKey, String treeResource) {
    this(List.of(new TreeDataset(datasetKey, treeResource)));
  }

  public TxtTreeDataRule(List<TreeDataset> treeData) {
    this(treeData, false, false);
  }

  /**
   * @param keepOrder if true stores the original txtree child order as the usages ordinal property
   */
  public TxtTreeDataRule(List<TreeDataset> treeData, boolean keepOrder, boolean buildMetrics) {
    this.datasets = treeData;
    sqlSessionFactorySupplier = SqlSessionFactoryRule::getSqlSessionFactory;
    this.keepOrder = keepOrder;
    this.buildMetrics = buildMetrics;
  }

  public static class TreeDataset {
    public final Integer key;
    public final String refResource;
    public final String treeResource;
    public final String title;
    public final DatasetOrigin origin;
    public final DatasetType type;

    public TreeDataset(Integer key, String refResource, String treeResource, String title, DatasetOrigin origin, DatasetType type) {
      this.key = key;
      this.refResource = refResource;
      this.treeResource = Preconditions.checkNotNull(treeResource);
      this.title = title;
      this.origin = Preconditions.checkNotNull(origin);
      this.type = Preconditions.checkNotNull(type);
    }
    public TreeDataset(Integer key, String treeResource) {
      this (key, null, treeResource, treeResource, DatasetOrigin.PROJECT, DatasetType.TAXONOMIC);
    }
    public TreeDataset(Integer key, String treeResource, String title) {
      this (key, null, treeResource, title, DatasetOrigin.PROJECT, DatasetType.TAXONOMIC);
    }
    public TreeDataset(Integer key, String treeResource, String title, DatasetOrigin origin) {
      this (key, null, treeResource, title, origin, DatasetType.TAXONOMIC);
    }
    public TreeDataset(Integer key, String folder, DatasetOrigin origin, DatasetType type) {
      this (key, folder+".bib", folder+".txtree", folder, origin, type);
    }
  }

  public static TxtTreeDataRule create(Map<Integer, TreeData> treeData) {
    return create(treeData, false, false);
  }

  public static TxtTreeDataRule create(Map<Integer, TreeData> treeData, boolean keepOrder, boolean buildMetrics) {
    var data = new ArrayList<TreeDataset>();
    for (Map.Entry<Integer, TreeData> x : treeData.entrySet()) {
      data.add(new TreeDataset(x.getKey(), x.getValue().resource(), x.getValue().name()));
    }
    return new TxtTreeDataRule(data, keepOrder, buildMetrics);
  }

  @Override
  public void before() throws Throwable {
    LOG.info("Load text trees");
    super.before();
    refID.set(1);
    initSession();
    for (TreeDataset x : datasets) {
      LOG.info("Loading dataset {} from tree {}", x.key, x.treeResource);
      createDataset(x);
      loadRefs(x);
      loadTree(x);
      createSequences(x);
      if (buildMetrics) {
        TaxonMetricsBuilder.rebuildMetrics(sqlSessionFactorySupplier.get(), x.key);
      }
      //printTree(x.key);
    }
  }

  public static void printTree(int datasetKey) throws IOException {
    Writer writer = new StringWriter();
    TreeTraversalParameter ttp = TreeTraversalParameter.dataset(datasetKey, null);
    var printer = PrinterFactory.dataset(TextTreePrinter.class, ttp, SqlSessionFactoryRule.getSqlSessionFactory(), writer);
    printer.showIDs();
    printer.print();

    System.out.println("\n*** DATASET "+datasetKey+" TREE ***");
    System.out.println(writer.toString().trim());
  }

  private void createDataset(TreeDataset td) {
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    Dataset d = dm.get(td.key);
    if (d == null) {
      d = TestEntityGenerator.newDataset("Tree " + td.treeResource);
      d.setKey(td.key);
      d.applyUser(Users.TESTER);
      d.setOrigin(td.origin);
      d.setType(td.type);
      dm.createWithID(d);
    }
    // create sequences
    if (d.getOrigin() == DatasetOrigin.PROJECT) {
      session.getMapper(DatasetPartitionMapper.class).createSequences(d.getKey());
    } else {
      // always create vernacular sequence so we can insert easily
      session.getMapper(DatasetPartitionMapper.class).createIdSequence("vernacular_name", d.getKey());
    }
    session.commit();
  }

  private void loadRefs(TreeDataset src) throws IOException, ParseException {
    if (src.refResource != null) {
      var stream = Resources.stream(src.refResource);
      if (stream != null) {
        LOG.debug("Inserting bibtex references for dataset {}", src.key);
        final ReferenceFactory refFactory = new ReferenceFactory(src.key);
        final BibTeXConverter bc = new BibTeXConverter();
        final BibTeXDatabase db = bc.loadDatabase(stream);
        bc.toItemData(db).forEach((id, cslItem) -> {
          CslData csl = CslDataConverter.toCslData(cslItem);
          csl.setId(id);
          Reference ref = refFactory.fromCsl(src.key, csl, IssueContainer.VOID);
          ref.setDatasetKey(src.key);
          ref.setId(id);
          ref.applyUser(Users.DB_INIT);
          rm.create(ref);
        });
      }
    }
  }

  private void loadTree(TreeDataset src) throws IOException, InterruptedException{
    var stream = Resources.stream(src.treeResource);
    Tree<SimpleTreeNode> tree = Tree.simple(stream);
    LOG.debug("Inserting {} usages for dataset {}", tree.size(), src.key);
    int ordinal = 1;
    for (SimpleTreeNode n : tree.getRoot()) {
      insertSubtree(src, null, n, ordinal++);
    }
  }

  private void insertSubtree(TreeDataset src, SimpleTreeNode parent, SimpleTreeNode t, int ordinal) throws InterruptedException {
    insertNode(src, parent, t, false, ordinal);
    for (SimpleTreeNode syn : t.synonyms) {
      insertNode(src, t, syn, true, null);
    }
    int childOrdinal = 1;
    for (SimpleTreeNode c : t.children) {
      insertSubtree(src, t, c, childOrdinal++);
    }
  }

  private void insertNode(TreeDataset src, SimpleTreeNode parent, SimpleTreeNode tn, boolean synonym, Integer ordinal) throws InterruptedException {
    Rank rank = SafeParser.parse(RankParser.PARSER, tn.rank).orElse(Rank.UNRANKED);
    NomCode code = null;
    if (tn.infos.containsKey(TxtTreeTerm.CODE.name())) {
      code = NomCodeParser.PARSER.parseOrNull(tn.infos.get(TxtTreeTerm.CODE.name())[0]);
    }
    ParsedNameUsage nat = NameParser.PARSER.parse(tn.name, rank, code, VerbatimRecord.VOID).get();
    Name n = nat.getName();
    if (code != null) {
      n.setCode(code);
    }
    if (tn.infos.containsKey(TxtTreeTerm.PUB.name())) {
      n.setPublishedInId(tn.infos.get(TxtTreeTerm.PUB.name())[0]);
    }

    n.setDatasetKey(src.key);
    n.setId(String.valueOf(tn.id));
    n.setOrigin(Origin.SOURCE);
    n.applyUser(Users.DB_INIT);
    nm.create(n);

    Integer sk = null;
    if (src.origin.isProjectOrRelease() && tn.infos.containsKey(KEY_PRIO)) {
      sk = Integer.parseInt(tn.infos.get(KEY_PRIO)[0]);
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
      t.setExtinct(tn.extinct);
      if (keepOrder) {
        t.setOrdinal(ordinal);
      }
      var status = TaxonomicStatus.ACCEPTED;
      if (tn.provisional) {
        status = TaxonomicStatus.PROVISIONALLY_ACCEPTED;
      }

      prepUsage(t, src.key, sk, nat, status, parent, tn);
      tm.create(t);

      if (tn.infos.containsKey(TxtTreeTerm.VERN.name())) {
        for (var x : tn.infos.get(TxtTreeTerm.VERN.name())) {
          var parts = x.split(":");
          var vn = new VernacularName();
          vn.setDatasetKey(src.key);
          vn.setSectorKey(sk);
          vn.setLanguage(parts[0]);
          vn.setName(parts[1]);
          vn.applyUser(Users.DB_INIT);
          CopyUtil.transliterateVernacularName(vn, IssueContainer.VOID);
          vm.create(vn, t.getId());
        }
      }
    }
  }

  private void prepUsage(NameUsageBase u, int datasetKey, Integer sectorKey, ParsedNameUsage nat, TaxonomicStatus status, SimpleTreeNode parent, SimpleTreeNode tn) {
    // ID given?
    if (tn.infos.containsKey(TxtTreeTerm.ID.name())) {
      u.setId( tn.infos.get(TxtTreeTerm.ID.name())[0] );
    } else {
      u.setId( String.valueOf(nat.getName().getId()) );
    }
    usageIDs.put(tn.id, u.getId());

    u.setDatasetKey(datasetKey);
    u.setSectorKey(sectorKey);
    u.setName(nat.getName());
    u.setOrigin(Origin.SOURCE);
    u.applyUser(Users.DB_INIT);
    u.setStatus(status);
    if (parent != null) {
      u.setParentId(usageIDs.get(parent.id));
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
      vm = session.getMapper(VernacularNameMapper.class);
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
