package life.catalogue;

import it.unimi.dsi.fastutil.Pair;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.importer.PgImportRule;
import life.catalogue.postgres.PgCopyUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.postgresql.jdbc.PgConnection;

/**
 * Manual tool to generate test data CSV files for the TestDataRule from regular CoLDP or DwC archives which are simpler to craft than sql dumps.
 * Can then be copied to the test data resources and used for quicker integration tests.
 * Run the respective "test" whenever you want the test resource to be changed based on newer archive imports.
 */

@Ignore("for manual use only")
public class TestDataGenerator {
  final static PgSetupRule pg = new PgSetupRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static TestDataRule dataRule = TestDataRule.empty();
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  final static TestDataRule.TestData MATCHING = new TestDataRule.TestData("matching", 101, 1, 3, Set.of(101));

   final static TestDataRule.TestData SYNCS = new TestDataRule.TestData("syncs", 3, 2, 4, null, Map.ofEntries(
    Map.entry(Pair.of(DataFormat.COLDP, 0),100),
    Map.entry(Pair.of(DataFormat.COLDP, 2),109),
    Map.entry(Pair.of(DataFormat.COLDP, 4),110),
    Map.entry(Pair.of(DataFormat.COLDP, 14),111),
    Map.entry(Pair.of(DataFormat.COLDP, 22),102),
    Map.entry(Pair.of(DataFormat.COLDP, 24),112),
    Map.entry(Pair.of(DataFormat.COLDP, 25),103),
    Map.entry(Pair.of(DataFormat.COLDP, 26),113),
    Map.entry(Pair.of(DataFormat.COLDP, 27),114),
    Map.entry(Pair.of(DataFormat.ACEF,1),101),
    Map.entry(Pair.of(DataFormat.ACEF,5),106),
    Map.entry(Pair.of(DataFormat.ACEF,6),107),
    Map.entry(Pair.of(DataFormat.ACEF,11),108),
    Map.entry(Pair.of(DataFormat.ACEF,14),116),
    Map.entry(Pair.of(DataFormat.DWCA,1),104),
    Map.entry(Pair.of(DataFormat.DWCA,2),105),
    Map.entry(Pair.of(DataFormat.DWCA,45),115)
  ));
  final static TestDataRule.TestData XCOL = new TestDataRule.TestData("xcol", 3, 2, 4, null);
  final static TestDataRule.TestData GROUPING = new TestDataRule.TestData("homgroup", 4, 2, 4, null);

  public static TestDataRule homotypigGrouping() {
    return new TestDataRule(GROUPING);
  }
  public static TestDataRule matching() {
    return new TestDataRule(MATCHING);
  }
  public static TestDataRule syncs() {
    return new TestDataRule(SYNCS);
  }
  public static TestDataRule xcol() {
    return new TestDataRule(XCOL);
  }

  final Taxon COL_ROOT = TestEntityGenerator.newMinimalTaxon(Datasets.COL, "root", null, Rank.UNRANKED, "Biota");

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(treeRepoRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  private File dir;

  @Test
  public void prepareMatchingData() throws Throwable {
    export(MATCHING.name,
      PgImportRule.create(DatasetOrigin.EXTERNAL, DatasetType.TAXONOMIC, DataFormat.COLDP, 28)
    );
  }

  @Test
  public void prepareSyncsData() throws Throwable {
    export(SYNCS.name,
      PgImportRule.create(
        DatasetOrigin.EXTERNAL,
          DataFormat.COLDP, 27, // both zoological and botanical
          NomCode.BOTANICAL,
            DataFormat.ACEF,  1,
            DataFormat.COLDP, 0, 22, 25,
            DataFormat.DWCA, 1, 2,
          NomCode.ZOOLOGICAL,
            DataFormat.ACEF,  5, 6, 11,
            DataFormat.COLDP, 2, 4, 14, 24, 26,
            DataFormat.DWCA, 45,
          NomCode.VIRUS,
            DataFormat.ACEF,  14
      )
    );
  }

  @Test
  public void prepareSyncedData() throws Throwable {
    export(TestDataRule.COL_SYNCED.name,
      PgImportRule.create(
        DatasetOrigin.EXTERNAL,
          NomCode.BOTANICAL,
            DataFormat.COLDP, 0, 25,
            DataFormat.DWCA, 1
      ), this::supplyMergedSectors
    );
  }

  List<Sector> supplyMergedSectors(PgImportRule importRule) {
    List<Sector> sectors = new ArrayList<>();
    sectors.add(
      sector(Sector.Mode.ATTACH,null, importRule.datasetKey(0, DataFormat.COLDP),
        SimpleNameLink.of("1", "Plantae", Rank.KINGDOM),
        SimpleNameLink.of(COL_ROOT)
      )
    );
    sectors.add(
      sector(Sector.Mode.MERGE,1, importRule.datasetKey(25, DataFormat.COLDP), null, null)
    );
    sectors.add(
      sector(Sector.Mode.MERGE,2, importRule.datasetKey(1, DataFormat.DWCA), null, null)
    );
    return sectors;
  }

  public static Sector sector(Sector.Mode mode, Integer priority, int datasetKey, SimpleNameLink src, SimpleNameLink target) {
    Sector sector = new Sector();
    sector.setMode(mode);
    sector.setPriority(priority);
    sector.setDatasetKey(Datasets.COL);
    sector.setSubjectDatasetKey(datasetKey);
    sector.setSubject(src);
    sector.setTarget(target);
    sector.setEntities(Set.of(EntityType.VERNACULAR, EntityType.DISTRIBUTION, EntityType.REFERENCE));
    sector.applyUser(TestDataRule.TEST_USER);
    return sector;
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

  @Test
  public void prepareHomotypicGroupingData() throws Throwable {
    export(GROUPING.name,
      PgImportRule.create(
        DatasetOrigin.PROJECT, DataFormat.COLDP, 30
      )
    );
  }

  void export(String name, PgImportRule importRule) throws Throwable {
    export(name, importRule, (x) -> List.of());
  }

  void export(String name, PgImportRule importRule, Function<PgImportRule, List<Sector>> sectorSupplier) throws Throwable {
    importRule.before();
    System.out.println("KEY MAP:");
    for (var ent : importRule.getDatasetKeyMap().entrySet()) {
      System.out.printf(" %s %s -> %s%n", ent.getKey().first(), ent.getKey().second(), ent.getValue());
    }
    for (var ent : importRule.getDatasetKeyMap().entrySet()) {
      System.out.printf(" Pair.of(DataFormat.%s, %s), %s,%n", ent.getKey().first(), ent.getKey().second(), ent.getValue());
    }

    // once we have datasets imported and a key map supply the sectors with proper subject keys
    var sectors = sectorSupplier.apply(importRule);
    if (!sectors.isEmpty()) {
      System.out.printf("Create COL project root taxon");
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        session.getMapper(NameMapper.class).create(COL_ROOT.getName());
        session.getMapper(TaxonMapper.class).create(COL_ROOT);
        session.commit();
      }

      System.out.printf("Create and sync %s sectors%n", sectors.size());
      for (var s : sectors) {
        try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
          session.getMapper(SectorMapper.class).create(s);
        }
        SectorSyncIT.sync(s);
      }
    }

    List<Dataset> datasets;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      datasets = dm.list(new Page(0,1000));
    }

    dir = new File("/Users/markus/Downloads/test-data/"+name);
    FileUtils.deleteQuietly(dir);
    dir.mkdirs();

    System.out.println("cd " + dir.getAbsolutePath());
    try (var con = pg.connect()) {
      // DATASET
      dump("dataset",
        "key,type,alias,title,origin,url,created_by,modified_by,created",
        "key != " + Datasets.COL, con
      );

      // SECTOR
      dump("sector",
        "dataset_key,id,subject_dataset_key,subject_id,subject_name,subject_rank,subject_status,subject_parent,original_subject_id,target_id,target_name,target_rank,mode,code,sync_attempt,dataset_attempt,created_by,modified_by,created",
        null, con
      );

      for (Dataset d : datasets) {
        System.out.println("Dump dataset " + d.getKey());
        dump(d, "reference", "id,sector_key,citation,year", con);
        dump(d, "name", "id, sector_key, scientific_name, authorship, rank, code, nom_status, type, candidatus, notho, "
                        + "uninomial, genus, infrageneric_epithet, specific_epithet, infraspecific_epithet, cultivar_epithet,"
                        + "basionym_authors, basionym_ex_authors, basionym_year, combination_authors, combination_ex_authors, combination_year, sanctioning_author, "
                        + "published_in_id, published_in_page, published_in_page_link, nomenclatural_note, unparsed, remarks, link"
                        , con);
        dump(d, "name_usage", "id,sector_key,name_id,parent_id,status,name_phrase,according_to_id,reference_ids,extinct,environments,link", con);
        dump(d, "vernacular_name", "id,taxon_id,language,country,name,latin,area,sex,reference_id", con);
        dump(d, "distribution", "id,taxon_id,gazetteer,status,area,reference_id", con);
        dump(d, "media", "id,taxon_id,type,captured,license,url,format,title,captured_by,link,reference_id", con);
        dump(d, "name_rel", "id,name_id,type,related_name_id,reference_id,remarks", con);
        dump(d, "type_material", "id,name_id,citation,status,country,locality,latitude,longitude,altitude,sex,institution_code,catalog_number,associated_sequences,host,date,collector,reference_id,link,remarks", con);
      }
    }

    importRule.after();
  }

  private void dump(String table, String columns, @Nullable String where, PgConnection con) throws SQLException, IOException {
    // only export if there is any data at all
    if (hasData(table, null, con)) {
      File f = new File(dir, table + ".csv");
      where = where == null ? "" : "WHERE " + where;
      String sql = String.format("SELECT %s FROM %s %s ORDER BY 1,2", columns, table, where);
      PgCopyUtils.dumpCSV(con, sql, f);
    }
  }

  private void dump(Dataset d, String table, String columns, PgConnection con) throws SQLException, IOException {
    // only export if there is any data at all
    if (hasData(table, d.getKey(), con)) {
      File f = new File(dir, table + "_" + d.getKey() + ".csv");
      String sql = String.format("SELECT %s FROM %s WHERE dataset_key=%s", columns, table, d.getKey());
      PgCopyUtils.dumpCSV(con, sql, f);
    }
  }

  private boolean hasData(String table, @Nullable Integer datasetKey, PgConnection con) throws SQLException {
    try (var st = con.createStatement()) {
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT * FROM ");
      sql.append(table);
      if (datasetKey != null) {
        sql.append(" WHERE dataset_key=");
        sql.append(datasetKey);
      }
      sql.append(" LIMIT 1");
      st.execute(sql.toString());
      return st.getResultSet().next();
    }
  }
}