package life.catalogue;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.importer.PgImportRule;
import life.catalogue.postgres.PgCopyUtils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.postgresql.jdbc.PgConnection;

/**
 * Manual tool to generate test data CSV files for the TestDataRule from regular CoLDP or DwC archives.
 * Can then be copied to the test data resources and used for quick tests.
 */

@Ignore("for manual use only")
public class TestDataGenerator {

  final static PgSetupRule pg = new PgSetupRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static TestDataRule dataRule = TestDataRule.empty();
  final static PgImportRule importRule = PgImportRule.create(DatasetOrigin.EXTERNAL, DatasetType.TAXONOMIC, DataFormat.COLDP, 28);

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(treeRepoRule)
    .around(importRule);

  private File dir;

  @Test
  public void exportTestData() throws Exception {
    List<Dataset> datasets;
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      datasets = dm.list(new Page(0,1000));
    }
    datasets.removeIf(d -> d.getKey() == Datasets.COL);

    dir = new File("/Users/markus/Downloads/test-data");
    FileUtils.deleteQuietly(dir);
    dir.mkdirs();

    System.out.println("cd " + dir.getAbsolutePath());
    try (var con = pg.connect()) {
      File fd = new File(dir, "dataset.csv");
      PgCopyUtils.dumpCSV(con, "SELECT key,type,alias,title,origin,url,created_by,modified_by,created FROM dataset WHERE key != " + Datasets.COL, fd);

      for (Dataset d : datasets) {
        System.out.println("Dump dataset " + d.getKey());
        dump(d, "reference", "id,citation", con);

        dump(d, "name", "id, scientific_name, authorship, rank, code, nom_status, type, candidatus, notho, "
                        + "uninomial, genus, infrageneric_epithet, specific_epithet, infraspecific_epithet, cultivar_epithet,"
                        + "basionym_authors, basionym_ex_authors, basionym_year, combination_authors, combination_ex_authors, combination_year, sanctioning_author, "
                        + "published_in_id, published_in_page, published_in_page_link, nomenclatural_note, unparsed, remarks, link"
                        , con);
        dump(d, "name_usage", "id,name_id,parent_id,status,name_phrase,according_to_id,reference_ids,extinct,environments,link", con);
      }
    }
  }

  private void dump(Dataset d, String table, String columns, PgConnection con) throws SQLException, IOException {
    File f = new File(dir, table + "_" + d.getKey() + ".csv");
    String sql = String.format("SELECT %s FROM %s WHERE dataset_key=%s", columns, table, d.getKey());
    PgCopyUtils.dumpCSV(con, sql, f);
  }
}