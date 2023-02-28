package life.catalogue.exporter;

import life.catalogue.ApiUtils;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.img.ImageService;
import life.catalogue.importer.PgImportRule;

import org.gbif.nameparser.api.NomCode;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.assertTrue;

/**
 * ColDP exporter test that uses the ColDP example file from the specs which has rather complete coverage of all entities!
 */
public class ColdpExportSpecsTest {

  static final WsServerConfig cfg = new WsServerConfig();
  static {
    cfg.apiURI= ApiUtils.API;
  }

  static PgSetupRule pgSetupRule = new PgSetupRule();
  static TestDataRule testDataRule = TestDataRule.empty();
  final static PgImportRule importRule = PgImportRule.create(
    NomCode.ZOOLOGICAL,
    DataFormat.COLDP, 0
  );

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pgSetupRule)
    .around(testDataRule)
    .around(importRule);

  @Test
  public void coldpSpecsExport() {
    MetricRegistry registry = new MetricRegistry();
    ExportRequest req = new ExportRequest(importRule.datasetKey(0, DataFormat.COLDP), DataFormat.COLDP);
    ColdpExport exp = new ColdpExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    System.out.println(exp.getArchive());
    assertTrue(exp.getArchive().exists());
  }

}
