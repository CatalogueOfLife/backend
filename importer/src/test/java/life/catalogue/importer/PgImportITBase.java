package life.catalogue.importer;

import life.catalogue.TestUtils;
import life.catalogue.api.RandomUtils;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSyncTestBase;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.es2.indexing.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.ImportStoreFactory;
import life.catalogue.importer.store.model.RankedName;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TreeRepoRule;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import life.catalogue.printer.PrinterUtils;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import com.google.common.io.Files;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import javax.annotation.Nullable;

import static org.junit.Assert.*;

/**
 *
 */
public class PgImportITBase {

  String resourceDir  ;
  ImportStore store;
  NormalizerConfig cfg;
  ImporterConfig icfg = new ImporterConfig();
  ImportStoreFactory importStoreFactory;
  DatasetWithSettings dataset;
  VerbatimRecordMapper vMapper;
  DatasetDao ddao;
  SynonymDao sdao;
  TaxonDao tdao;
  TreeDao treeDao;
  NameDao ndao;
  ReferenceDao rdao;
  Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  NameUsageIndexService indexService = NameUsageIndexService.passThru();

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    importStoreFactory = new ImportStoreFactory(cfg);
    dataset = new DatasetWithSettings();
    dataset.setType(DatasetType.OTHER);
    dataset.setOrigin(DatasetOrigin.EXTERNAL);
    dataset.setCreatedBy(TestDataRule.TEST_USER.getKey());
    dataset.setModifiedBy(TestDataRule.TEST_USER.getKey());

    sdao = new SynonymDao(SqlSessionFactoryRule.getSqlSessionFactory(), ndao, indexService, validator);
    ndao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), ndao, null, new ThumborService(new ThumborConfig()), indexService, null, validator);
    rdao = new ReferenceDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, validator);
    ddao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null,null, validator, TestUtils.mockedBroker());
    treeDao = new TreeDao(SqlSessionFactoryRule.getSqlSessionFactory());
  }
  
  @After
  public void cleanup() throws Exception {
    if (store != null) {
      store.close();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }

  void assertTree() throws IOException {
    // compare with expected tree
    SectorSyncTestBase.assertTree(dataset.getTitle(), dataset.getKey(), getClass().getResourceAsStream(resourceDir + "/expected.tree"));
  }

  void printTree() throws Exception {
    PrinterUtils.print(dataset.getKey(), true, SqlSessionFactoryRule.getSqlSessionFactory());
  }

  void normalizeAndImport(DataFormat format, int key) throws Exception {
    resourceDir = "/" + format.name().toLowerCase() + "/" + key;
    URL url = getClass().getResource(resourceDir);
    dataset.setDataFormat(format);
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
      store = importStoreFactory.create(dataset.getKey(), 1);
      Normalizer norm = new Normalizer(dataset, store, source,
        NameIndexFactory.build(NamesIndexConfig.memory(1024), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE).started(),
        ImageService.passThru(), validator, null);
      norm.call();
      
      // import into postgres
      PgImport importer = new PgImport(1, DOI.test(RandomUtils.randomLatinString(20)), dataset, Users.IMPORTER, store, SqlSessionFactoryRule.getSqlSessionFactory(), icfg, ddao, indexService);
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
    assertTrue(v.contains(issue));
  }
  
  void assertNoIssue(VerbatimEntity ent, Issue issue) {
    VerbatimRecord v = vMapper.get(DSID.vkey(ent));
    assertFalse(v.contains(issue));
  }
  
  public static List<Distribution> expectedDwca24Distributions() {
    // TDWG:MOR-CE & TDWG:MOR-ME do not exist - will be removed
    List<Distribution> expD = new ArrayList<>();
    expD.add(dist(new AreaImpl("All of Austria and the alps"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("DE"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("FR"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("DK"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("GB"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("NG"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("KE"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(TdwgArea.of("AGS"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(new AreaImpl(Gazetteer.FAO, "37.4.1"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(TdwgArea.of("MOR-MO"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(TdwgArea.of("CPP"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(TdwgArea.of("NAM"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("IT"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("ES"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("FR"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    expD.add(dist(Country.fromIsoCode("FM"), EstablishmentMeans.NATIVE, DegreeOfEstablishment.NATIVE));
    return expD;
  }
  
  static RankedName rn(Rank rank, String name) {
    return new RankedName(null, name, null, rank);
  }

  static Distribution dist(Optional<? extends Area> area, EstablishmentMeans means, DegreeOfEstablishment degree) {
    return dist(area.get(), means, degree);
  }
  static Distribution dist(Area area, EstablishmentMeans means, DegreeOfEstablishment degree) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setEstablishmentMeans(means);
    d.setDegreeOfEstablishment(degree);
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
