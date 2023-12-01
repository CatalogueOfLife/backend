package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.*;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;

import static org.junit.Assert.*;

public abstract class SectorSyncTestBase {


  public static List<NameUsageBase> listByName(int datasetKey, Rank rank, String name) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(NameUsageMapper.class).listByName(datasetKey, name, rank, new Page(0,100));
    }
  }

  public static NameUsageBase getByName(int datasetKey, Rank rank, String name) {
    List<NameUsageBase> taxa = listByName(datasetKey, rank, name);
    if (taxa.isEmpty()) return null;
    if (taxa.size() > 1) throw new IllegalStateException("Multiple taxa found for name="+name);
    return taxa.get(0);
  }

  public static List<SimpleName> getClassification(DSID<String> taxon) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(TaxonMapper.class).classificationSimple(taxon);
    }
  }

  public static VerbatimSource getSource(DSID<String> key) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      return session.getMapper(VerbatimSourceMapper.class).get(key);
    }
  }

  NameUsageBase getByID(String id) {
    return getByID(Datasets.COL, id);
  }

  NameUsageBase getByID(int datasetKey, String id) {
    return getByID(DSID.of(datasetKey, id));
  }

  NameUsageBase getByID(DSID<String> id) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(TaxonMapper.class).get(id);
    }
  }

  Taxon getDraftTaxonBySourceID(int sourceDatasetKey, String id) {
    Taxon src;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      src = tm.get(DSID.of(sourceDatasetKey, id));
    }
    return (Taxon) getByName(Datasets.COL, src.getName().getRank(), src.getName().getScientificName());
  }

  static SimpleNameLink simple(NameUsageBase nu) {
    return nu == null ? null : nu.toSimpleNameLink();
  }

  public static DSID<Integer> createSector(Sector.Mode mode, int srcDatasetKey, NameUsageBase target) {
    return createSector(mode, srcDatasetKey, null, simple(target));
  }

  public static DSID<Integer> createSector(Sector.Mode mode, NameUsageBase src, NameUsageBase target) {
    return createSector(mode, src.getDatasetKey(), simple(src), simple(target));
  }

  public static DSID<Integer> createSector(Sector.Mode mode, NameUsageBase src, NameUsageBase target, Consumer<Sector> modifier) {
    return createSector(mode, null, src.getDatasetKey(), simple(src), simple(target), modifier);
  }

  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, NameUsageBase src, NameUsageBase target) {
    return createSector(mode, datasetKey, simple(src), simple(target));
  }
  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, NameUsageBase src, NameUsageBase target, Consumer<Sector> modifier) {
    return createSector(mode, null, datasetKey, simple(src), simple(target), modifier);
  }

  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, SimpleNameLink src, SimpleNameLink target) {
    return createSector(mode, null, datasetKey, src, target, null);
  }

  public static DSID<Integer> createSector(Sector.Mode mode, Integer priority, int subjectDatasetKey, SimpleNameLink src, SimpleNameLink target, Consumer<Sector> modifier) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setPriority(priority);
      sector.setDatasetKey(Datasets.COL);
      sector.setSubjectDatasetKey(subjectDatasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.setEntities(Set.of(EntityType.values()));
      sector.setNameTypes(Set.of(NameType.SCIENTIFIC, NameType.VIRUS, NameType.HYBRID_FORMULA));
      sector.applyUser(TestDataRule.TEST_USER);
      if (modifier != null) {
        modifier.accept(sector);
      }
      session.getMapper(SectorMapper.class).create(sector);
      return sector;
    }
  }

  public static EditorialDecision createDecision(int datasetKey, SimpleNameLink src, EditorialDecision.Mode mode, Name name, @Nullable TaxonomicStatus status) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      EditorialDecision ed = new EditorialDecision();
      ed.setMode(mode);
      ed.setDatasetKey(Datasets.COL);
      ed.setSubjectDatasetKey(datasetKey);
      ed.setSubject(src);
      ed.setName(name);
      ed.setStatus(status);
      ed.applyUser(TestDataRule.TEST_USER);
      session.getMapper(DecisionMapper.class).create(ed);
      return ed;
    }
  }

  public void syncAll() {
    syncAll(null);
  }

  public void syncMergesOnly() {
    syncAll(s -> s.getMode() == Sector.Mode.MERGE);
  }

  public static List<SectorImport> syncAll(@Nullable Predicate<Sector> filter) {
    List<SectorImport> imports = new ArrayList<>();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      for (Sector s : session.getMapper(SectorMapper.class).list(Datasets.COL, null)) {
        if (filter == null || filter.test(s)) {
          imports.add(sync(s));
        }
      }
    }
    return imports;
  }

  /**
   * Syncs into the project
   */
  public static SectorImport sync(Sector s) {
    SectorSync ss = SyncFactoryRule.getFactory().project(s, SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    if (s.getNote() != null && s.getNote().contains("disableAutoBlocking")) {
      ss.setDisableAutoBlocking(true);
    }
    return runSync(ss);
  }

  void disableAutoBlocking(Sector s) {
    s.setNote("disableAutoBlocking");
  }

  private static SectorImport runSync(SectorSync ss) {
    System.out.println("\n*** SECTOR " + ss.sector.getMode() + " SYNC " + ss.sectorKey + " ***");
    ss.run();
    if (ss.getState().getState() != ImportState.FINISHED){
      throw new IllegalStateException("SectorSync failed with error: " + ss.getState().getError());
    }
    return ss.getState();
  }
  void deleteFull(Sector s) {
    SectorDeleteFull sd = SyncFactoryRule.getFactory().deleteFull(s, SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR FULL DELETION " + s.getKey() + " ***");
    sd.run();
  }

  void delete(Sector s) {
    SectorDelete sd = SyncFactoryRule.getFactory().delete(s, SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR DELETION " + s.getKey() + " ***");
    sd.run();
  }

  void print(int datasetKey) throws Exception {
    StringWriter writer = new StringWriter();
    writer.append("\nDATASET "+datasetKey+"\n");
    PrinterFactory.dataset(TextTreePrinter.class, datasetKey, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    System.out.println(writer.toString());
  }

  void print(String filename) throws Exception {
    System.out.println("\n" + filename);
    InputStream resIn = openResourceStream(filename);
    String tree = UTF8IoUtils.readString(resIn).trim();
    System.out.println(tree);
  }

  InputStream openResourceStream(String filename) {
    return getClass().getResourceAsStream("/assembly-trees/" + filename);
  }


  void assertTree(String filename) throws IOException {
    assertTree(Datasets.COL, openResourceStream(filename));
  }

  public static void assertSameTree(int datasetKey1, int datasetKey2) throws IOException {
    String tree1 = readTree(datasetKey1, null);
    System.out.println("\n*** DATASET "+datasetKey1+" TREE ***");
    System.out.println(tree1);

    String tree2 = readTree(datasetKey2, null);
    System.out.println("\n*** DATASET "+datasetKey2+" TREE ***");
    System.out.println(tree2);

    // compare trees
    assertEquals("Tree not as expected for datasets " + datasetKey1 + " and "+datasetKey2, tree1, tree2);
  }

  public static void assertTree(int datasetKey, InputStream expectedTree) throws IOException {
    assertTree(datasetKey, null, expectedTree);
  }
  public static void assertTree(int datasetKey, @Nullable String rootID, InputStream expectedTree) throws IOException {
    String expected = UTF8IoUtils.readString(expectedTree).trim();
    String tree = readTree(datasetKey, rootID);

    // compare trees
    System.out.println("\n*** DATASET "+datasetKey+" TREE ***");
    System.out.println(tree);
    assertEquals("Tree not as expected for dataset " + datasetKey, expected, tree);
  }

  public static String readTree(int datasetKey,@Nullable String rootID) throws IOException {
    Writer writer = new StringWriter();
    TreeTraversalParameter ttp = TreeTraversalParameter.dataset(datasetKey, rootID);
    PrinterFactory.dataset(TextTreePrinter.class, ttp, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    String tree = writer.toString().trim();
    assertFalse("Empty tree, probably no root node found", tree.isEmpty());
    return tree;
  }

  void assertHasVerbatimSource(DSID<String> id, String expectedSourceId) {
    VerbatimSource v = getSource(id);
    assertEquals(id.getId(), v.getId());
    assertEquals(id.getDatasetKey(), v.getDatasetKey());
    assertNotNull(v.getSourceDatasetKey());
    assertEquals(expectedSourceId, v.getSourceId());
  }


  Sector sector(DSID<Integer> key) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(SectorMapper.class).get(key);
    }
  }

}
