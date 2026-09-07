package life.catalogue.exporter;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An archive names its sources, but must not repeat the project's entire editor list on every one of them.
 * A release with thousands of sources otherwise carries megabytes of editor names that no citation style can
 * even render - APA shows the first 19 and "et al.". See ArchiveExport.exportMetadata.
 */
public class ColdpExtendedExportSourcesIT extends ExportTest {
  static final int PROJECT_KEY = 3;
  static final int SOURCE_KEY = 100;

  public ColdpExtendedExportSourcesIT() {
    super(TestDataRule.DRAFT_WITH_SECTORS);
  }

  @Before
  public void addProjectCreators() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      var d = dm.get(PROJECT_KEY);
      d.setCreator(Agent.parse(List.of("Banki, Olaf", "Roskov, Yuri")));
      d.applyUser(Users.TESTER);
      dm.update(d);
    }
  }

  @Test
  public void sourceCitationsCarryNoContainerCreators() throws Exception {
    ExportRequest req = new ExportRequest(PROJECT_KEY, DataFormat.COLDP);
    req.setExtended(true);
    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    String metadata = readArchiveEntry(exp.getArchive(), "metadata.yaml");
    // the project keeps its own creators, they are real metadata
    assertTrue(metadata, metadata.contains("Roskov"));

    // the source is listed and still says which project it was released in
    String sources = metadata.substring(metadata.indexOf("\nsource:"));
    assertTrue(sources, sources.contains("GSD x"));
    assertTrue(sources, sources.contains("containerTitle: Catalogue of Life"));
    // but does not repeat who edited that project
    assertFalse(sources, sources.contains("containerAuthor"));
    assertFalse(sources, sources.contains("Roskov"));

    // same for the per source sidecar, which serialises the source Dataset itself
    String sidecar = readArchiveEntry(exp.getArchive(), "source/" + SOURCE_KEY + ".yaml");
    assertTrue(sidecar, sidecar.contains("containerTitle: Catalogue of Life"));
    assertFalse(sidecar, sidecar.contains("containerCreator"));
    assertFalse(sidecar, sidecar.contains("Roskov"));
  }
}
