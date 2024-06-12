package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.neo.model.RankedName;
import life.catalogue.matching.NameIndexFactory;

import life.catalogue.matching.NamesIndexConfig;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import com.google.common.eventbus.EventBus;
import com.google.common.io.Files;

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
  DatasetDao ddao;
  SynonymDao sdao;
  TaxonDao tdao;
  NameDao ndao;
  ReferenceDao rdao;
  EventBus bus = new EventBus();
  Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
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

    sdao = new SynonymDao(SqlSessionFactoryRule.getSqlSessionFactory(), ndao, indexService, validator);
    ndao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), ndao, indexService, validator);
    rdao = new ReferenceDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, validator);
    ddao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null,null, validator);
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

  void normalizeAndImport(DatasetWithSettings ds) throws Exception {
    dataset = ds;
    URL url = getClass().getResource("/" + ds.getDataFormat().name().toLowerCase() + "/" + ds.getKey());
    normalizeAndImport(Paths.get(url.toURI()));
  }

  void normalizeAndImport(Path source) {
    try {
      // insert trusted dataset
      dataset.setTitle("Test Dataset " + source.toString());
      
      if (dataset.getKey() == null) {
        // this creates a new key, usually 101!
        ddao.create(dataset, Users.IMPORTER);
      } else {
        ddao.update(dataset.getDataset(), Users.IMPORTER);
      }

      // normalize
      store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
      Normalizer norm = new Normalizer(dataset, store, source,
        NameIndexFactory.memory(NamesIndexConfig.memory(1024), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE).started(),
        ImageService.passThru(), validator, null);
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
      PgImport importer = new PgImport(1, dataset, Users.IMPORTER, store, SqlSessionFactoryRule.getSqlSessionFactory(), icfg, ddao, indexService);
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
    try(SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()){
      NameMapper nm = session.getMapper(NameMapper.class);
      for (Name n : nm.processDataset(datasetKey)) {
        assertNotNull(n.getNamesIndexId());
        assertNotNull(n.getNamesIndexType());
      }
    }
  }

  DatasetImport metrics() {
    return new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo())
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
  
  public static List<Distribution> expectedDwca24Distributions() {
    // TDWG:MOR-CE & TDWG:MOR-ME do not exist - will be removed
    List<Distribution> expD = new ArrayList<>();
    expD.add(dist(new AreaImpl("All of Austria and the alps"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("DE"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("FR"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("DK"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("GB"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("NG"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("KE"), DistributionStatus.NATIVE));
    expD.add(dist(TdwgArea.of("AGS"), DistributionStatus.NATIVE));
    expD.add(dist(new AreaImpl(Gazetteer.FAO, "37.4.1"), DistributionStatus.NATIVE));
    expD.add(dist(TdwgArea.of("MOR-MO"), DistributionStatus.NATIVE));
    expD.add(dist(TdwgArea.of("CPP"), DistributionStatus.NATIVE));
    expD.add(dist(TdwgArea.of("NAM"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("IT"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("ES"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("FR"), DistributionStatus.NATIVE));
    expD.add(dist(Country.fromIsoCode("FM"), DistributionStatus.NATIVE));
    return expD;
  }
  
  static RankedName rn(Rank rank, String name) {
    return new RankedName(null, name, null, rank);
  }

  static Distribution dist(Optional<? extends Area> area, DistributionStatus status) {
    return dist(area.get(), status);
  }
  static Distribution dist(Area area, DistributionStatus status) {
    Distribution d = new Distribution();
    d.setArea(area);
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
