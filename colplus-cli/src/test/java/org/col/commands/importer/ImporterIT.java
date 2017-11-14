package org.col.commands.importer;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.Name;
import org.col.api.Taxon;
import org.col.commands.config.ImporterConfig;
import org.col.commands.config.NormalizerConfig;
import org.col.commands.importer.dwca.Normalizer;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.dao.NameDao;
import org.col.dao.TaxonDao;
import org.col.db.mapper.*;
import org.junit.*;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ImporterIT {
  private NormalizerStore store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private Dataset dataset;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.empty();

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.directory = Files.createTempDir();
    dataset = new Dataset();
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      // store is close by Normalizer.run method already
      FileUtils.deleteQuietly(cfg.directory);
    }
  }

  void normalizeAndImport(int dwcaKey) throws Exception {
    URL dwcaUrl = getClass().getResource("/dwca/"+dwcaKey);
    Path dwca = Paths.get(dwcaUrl.toURI());

    // insert dataset
    dataset.setTitle("Test Dataset " + dwcaKey);
    SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
    // this creates a new datasetKey, usually 1!
    session.getMapper(DatasetMapper.class).create(dataset);
    session.commit();
    session.close();

    // normalize
    Normalizer norm = new Normalizer(NeoDbFactory.create(cfg, dataset.getKey()), dwca.toFile());
    norm.run();

    // import into postgres
    store = NeoDbFactory.open(cfg, dataset.getKey());
    Importer importer = new Importer(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(), icfg);
    importer.run();
  }

  @Test
  public void testDwca1() throws Exception {
    normalizeAndImport(1);

    // verify results
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      TaxonDao tdao = new TaxonDao(session);
      NameDao ndao = new NameDao(session);

      // check basionym
      Name n1006 = ndao.get(dataset.getKey(), "1006");
      assertEquals("Leontodon taraxacoides", n1006.getScientificName());

      Name bas = ndao.get(n1006.getBasionym().getKey());
      assertEquals("Leonida taraxacoida", bas.getScientificName());
      assertEquals("1006-s3", bas.getId());

      // check taxon parents
      assertParents(tdao, "1006", "102", "30", "20", "10", "1");

      // TODO: check synonym
    }
  }

  private void assertParents(TaxonDao tdao, String taxonID, String ... parentIds) {
    final LinkedList<String> expected = new LinkedList<String>(Arrays.asList(parentIds));
    Taxon t = tdao.get(dataset.getKey(), taxonID);
    while (t.getParentKey() != null) {
    	Taxon parent = tdao.get(t.getParentKey());
      assertEquals(expected.pop(), parent.getId());
      t = parent;
    }
    assertTrue(expected.isEmpty());
  }

}