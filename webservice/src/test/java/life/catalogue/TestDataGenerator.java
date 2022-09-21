package life.catalogue;

import com.google.common.base.Preconditions;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.FileMetricsDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.importer.PgImportRule;
import life.catalogue.postgres.PgCopyUtils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.NomCode;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manual tool to generate test data CSV files for the TestDataRule from regular CoLDP or DwC archives.
 * Can then be copied to the test data resources and used for quicker integration tests.
 */

@Ignore("for manual use only")
public class TestDataGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(TestDataGenerator.class);

  final static PgSetupRule pg = new PgSetupRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static TestDataRule dataRule = TestDataRule.empty();

  public final static TestDataRule.TestData MATCHING = new TestDataRule.TestData("matching", 101, 1, 3, Set.of(101));
  public final static TestDataRule.TestData SYNCS = new TestDataRule.TestData("syncs", 3, 1, 3, null);
  public final static TestDataRule.TestData XCOL = new TestDataRule.TestData("xcol", 3, 1, 3, null);

  public static TestDataRule matching() {
    return new TestDataRule(MATCHING);
  }
  public static TestDataRule syncs() {
    return new TestDataRule(SYNCS);
  }
  public static TestDataRule xcol() {
    return new TestDataRule(XCOL);
  }

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(treeRepoRule);

  private File dir;

  @Test
  public void prepareMatchingData() throws Throwable {
    export("matching",
      PgImportRule.create(DatasetOrigin.EXTERNAL, DatasetType.TAXONOMIC, DataFormat.COLDP, 28)
    );
  }

  @Test
  public void prepareSyncsData() throws Throwable {
    export("syncs",
      PgImportRule.create(
        DatasetOrigin.EXTERNAL,
          NomCode.BOTANICAL,
            DataFormat.ACEF,  1,
            DataFormat.COLDP, 0, 22, 25,
            DataFormat.DWCA, 1, 2,
          NomCode.ZOOLOGICAL,
            DataFormat.ACEF,  5, 6, 11,
            DataFormat.COLDP, 2, 4, 14, 24, 26, 27,
          NomCode.VIRUS,
            DataFormat.ACEF,  14
      )
    );
  }

  @Test
  public void prepareXcolData() throws Throwable {
    List<Object> params = new ArrayList<>();
    params.addAll(List.of(DatasetOrigin.EXTERNAL, DataFormat.TEXT_TREE, DatasetType.TAXONOMIC));
    for (int key = 101; key<200; key++) {
      String res = String.format("xcol/%s.txt", key);
      var url = getClass().getClassLoader().getResource(res);
      if (url != null) {
        params.add(res);
        params.add(key);
      }
    }
    export("xcol", PgImportRule.create(params.toArray()));
    // also add in the draft test data resources!
    for (String fn : List.of("name_3.csv", "name_usage_3.csv")) {
      Resources.copy("/test-data/" + TestDataRule.DRAFT.name + "/" + fn, new File(dir, fn));
    }
  }

  void export(String name, PgImportRule importRule) throws Throwable {
    importRule.before();
    System.out.println("KEY MAP:");
    for (var ent : importRule.getDatasetKeyMap().entrySet()) {
      System.out.println(String.format(" %s %s -> %s", ent.getKey().first(), ent.getKey().second(), ent.getValue()));
    }

    List<Dataset> datasets;
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      datasets = dm.list(new Page(0,1000));
    }
    datasets.removeIf(d -> d.getKey() == Datasets.COL);

    dir = new File("/Users/markus/Downloads/test-data/"+name);
    FileUtils.deleteQuietly(dir);
    dir.mkdirs();

    System.out.println("cd " + dir.getAbsolutePath());
    try (var con = pg.connect()) {
      File fd = new File(dir, "dataset.csv");
      PgCopyUtils.dumpCSV(con,
        "SELECT key,type,alias,title,origin,url,created_by,modified_by,created FROM dataset WHERE key != " + Datasets.COL + " ORDER BY key"
      , fd);

      for (Dataset d : datasets) {
        System.out.println("Dump dataset " + d.getKey());
        dump(d, "reference", "id,citation", con);
        dump(d, "name", "id, scientific_name, authorship, rank, code, nom_status, type, candidatus, notho, "
                        + "uninomial, genus, infrageneric_epithet, specific_epithet, infraspecific_epithet, cultivar_epithet,"
                        + "basionym_authors, basionym_ex_authors, basionym_year, combination_authors, combination_ex_authors, combination_year, sanctioning_author, "
                        + "published_in_id, published_in_page, published_in_page_link, nomenclatural_note, unparsed, remarks, link"
                        , con);
        dump(d, "name_usage", "id,name_id,parent_id,status,name_phrase,according_to_id,reference_ids,extinct,environments,link", con);
        dump(d, "vernacular_name", "id,taxon_id,language,country,name,latin,area,sex,reference_id", con);
        dump(d, "distribution", "id,taxon_id,gazetteer,status,area,reference_id", con);
        dump(d, "media", "id,taxon_id,type,captured,license,url,format,title,captured_by,link,reference_id", con);
        dump(d, "name_rel", "id,name_id,type,related_name_id,reference_id,remarks", con);
        dump(d, "type_material", "id,name_id,citation,status,country,locality,latitude,longitude,altitude,sex,institution_code,catalog_number,associated_sequences,host,date,collector,reference_id,link,remarks", con);
      }
    }

    importRule.after();
  }

  private void dump(Dataset d, String table, String columns, PgConnection con) throws SQLException, IOException {
    File f = new File(dir, table + "_" + d.getKey() + ".csv");
    String sql = String.format("SELECT %s FROM %s WHERE dataset_key=%s", columns, table, d.getKey());
    // check if there is any data at all
    try (var st = con.createStatement()) {
      st.execute(sql + " limit 1");
      if (!st.getResultSet().next()) {
        LOG.info("No {} data for dataset {}", table, d.getKey());
        return;
      }
    }

    PgCopyUtils.dumpCSV(con, sql, f);
  }
}