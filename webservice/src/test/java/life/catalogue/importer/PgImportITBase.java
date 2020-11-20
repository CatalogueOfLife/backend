package life.catalogue.importer;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.command.InitDbCmd;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.neo.model.RankedName;
import life.catalogue.matching.NameIndexFactory;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;

import static org.junit.Assert.*;

/**
 *
 */
public class PgImportITBase {
  
  NeoDb store;
  NormalizerConfig cfg;
  ImporterConfig icfg = new ImporterConfig();
  DatasetWithSettings dataset;
  VerbatimRecordMapper vMapper;
  boolean fullInit = true;
  SynonymDao sdao;
  TaxonDao tdao;
  NameDao ndao;
  ReferenceDao rdao;
  NameUsageIndexService indexService = NameUsageIndexService.passThru();

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  @Before
  public void initCfg() {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    dataset = new DatasetWithSettings();
    dataset.setType(DatasetType.OTHER);
    dataset.setOrigin(DatasetOrigin.EXTERNAL);
    dataset.setCreatedBy(TestDataRule.TEST_USER.getKey());
    dataset.setModifiedBy(TestDataRule.TEST_USER.getKey());

    if (fullInit) {
      InitDbCmd.setupColPartition(testDataRule.getSqlSession());
      testDataRule.commit();
    }

    sdao = new SynonymDao(PgSetupRule.getSqlSessionFactory());
    ndao = new NameDao(PgSetupRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru());
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), ndao, indexService);
    rdao = new ReferenceDao(PgSetupRule.getSqlSessionFactory());
  }
  
  @After
  public void cleanup() {
    if (store != null) {
      store.closeAndDelete();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
  
  void normalizeAndImport(DataFormat format, int key) throws Exception {
    URL url = getClass().getResource("/" + format.name().toLowerCase() + "/" + key);
    dataset.setDataFormat(format);
    normalizeAndImport(Paths.get(url.toURI()));
  }
  
  void normalizeAndImport(Path source) {
    try {
      // insert trusted dataset
      dataset.setTitle("Test Dataset " + source.toString());
      
      SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
      // this creates a new key, usually 2000!
      session.getMapper(DatasetMapper.class).createAll(dataset);
      session.commit();
      session.close();
      
      // normalize
      store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
      Normalizer norm = new Normalizer(dataset, store, source,
        NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE).started(),
        ImageService.passThru());
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
      PgImport importer = new PgImport(1, dataset, store, PgSetupRule.getSqlSessionFactory(), icfg, indexService);
      importer.call();
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  void normalizeAndImport(URI url, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // download an decompress
    ExternalSourceUtil.consumeSource(url, this::normalizeAndImport);
  }
  
  void normalizeAndImportArchive(File file, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // decompress
    ExternalSourceUtil.consumeFile(file, this::normalizeAndImport);
  }
  
  void normalizeAndImportFolder(File folder, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // decompress
    normalizeAndImport(folder.toPath());
  }

  void verifyNamesIndexIds(int datasetKey){
    try(SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()){
      NameMapper nm = session.getMapper(NameMapper.class);
      for (NameMapper.NameWithNidx n : nm.processDatasetWithNidx(datasetKey)) {
        assertNotNull(n.namesIndexId);
        assertNotNull(n.namesIndexType);
      }
    }
  }

  DatasetImport metrics() {
    return new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo())
        .generateMetrics(dataset.getKey(), Users.TESTER);
  }
  
  void assertIssue(VerbatimEntity ent, Issue issue) {
    VerbatimRecord v = vMapper.get(DSID.vkey(ent));
    assertTrue(v.hasIssue(issue));
  }
  
  void assertNoIssue(VerbatimEntity ent, Issue issue) {
    VerbatimRecord v = vMapper.get(DSID.vkey(ent));
    assertFalse(v.hasIssue(issue));
  }
  
  public static Set<Distribution> expectedDwca24Distributions() {
    Set<Distribution> expD = Sets.newHashSet();
    expD.add(dist(Gazetteer.TEXT, "All of Austria and the alps", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "DE", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "FR", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "DK", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "GB", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "NG", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "KE", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "AGS", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.FAO, "37.4.1", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "MOR-MO", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "MOR-CE", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "MOR-ME", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "CPP", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "NAM", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "IT-82", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "ES-CN", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "FR-H", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "FM-PNI", DistributionStatus.NATIVE));
    return expD;
  }
  
  static RankedName rn(Rank rank, String name) {
    return new RankedName(null, name, null, rank);
  }
  
  static Distribution dist(Gazetteer standard, String area, DistributionStatus status) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setGazetteer(standard);
    d.setStatus(status);
    return d;
  }
  
  void assertParents(TaxonDao tdao, String taxonID, String... parentIds) {
    final LinkedList<String> expected = new LinkedList<String>(Arrays.asList(parentIds));
    Taxon t = tdao.get(key(dataset.getKey(), taxonID));
    while (t.getParentId() != null) {
      Taxon parent = tdao.get(key(dataset.getKey(), t.getParentId()));
      assertEquals(expected.pop(), parent.getId());
      t = parent;
    }
    assertTrue(expected.isEmpty());
  }
  
  public static DSID<String> key(int datasetKey, String id) {
    return new DSIDValue<>(datasetKey, id);
  }
  
}
