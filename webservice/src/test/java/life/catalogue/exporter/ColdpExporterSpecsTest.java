package life.catalogue.exporter;

import life.catalogue.ApiUtils;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.img.ImageService;
import life.catalogue.importer.PgImportRule;

import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.NomCode;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertTrue;

/**
 * ColDP exporter test that uses the ColDP example file from the specs which has rather complete coverage of all entities!
 */
public class ColdpExporterSpecsTest {

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
    ExportRequest req = new ExportRequest(importRule.datasetKey(0, DataFormat.COLDP), DataFormat.COLDP);
    ColdpExporter exp = new ColdpExporter(req, Users.TESTER, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    System.out.println(exp.getArchive());
    assertTrue(exp.getArchive().exists());
  }

}
