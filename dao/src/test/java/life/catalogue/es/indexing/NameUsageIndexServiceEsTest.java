package life.catalogue.es.indexing;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.common.io.TempFile;
import life.catalogue.config.EsConfig;
import life.catalogue.config.IndexConfig;
import life.catalogue.es.EsSetupRule;
import life.catalogue.es.EsTestBase;

import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class NameUsageIndexServiceEsTest {

  static PgSetupRule pgSetupRule = new PgSetupRule();
  static TestDataRule testDataRule = TestDataRule.draftWithSectors();
  static EsSetupRule esSetup = new EsSetupRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pgSetupRule)
    .around(testDataRule)
    .around(esSetup);

  NameUsageIndexServiceEs service;
  TempFile dir;
  final int datasetKey = 3;

  @Before
  public void init() throws IOException {
    dir = TempFile.directory();
    var client = esSetup.getClient();
    var cfg = esSetup.getEsConfig();
    service = new NameUsageIndexServiceEs(client, cfg, dir.file, PgSetupRule.getSqlSessionFactory());
    // create empty index
    service.createEmptyIndex();
  }

  @After
  public void tearDown() throws IOException {
    dir.close();
  }

  @Test
  public void indexDataset() {
    service.indexDataset(datasetKey);
  }

  @Test
  public void deleteDataset() {
    service.deleteDataset(datasetKey);
  }

  @Test
  public void indexSector() {
    service.indexSector(DSID.of(datasetKey, 100));
  }

  @Test
  public void deleteSector() {
    service.deleteSector(DSID.of(datasetKey, 100));
  }

  @Test
  public void createEmptyIndex() {
    service.createEmptyIndex();
  }
}