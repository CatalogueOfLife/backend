package org.col.admin.assembly;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.admin.importer.PgImportRule;
import org.col.api.model.Sector;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Datasets;
import org.col.db.PgSetupRule;
import org.col.db.dao.DatasetImportDao;
import org.col.db.mapper.InitMybatisRule;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.db.printer.TextTreePrinter;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SectorSyncIT {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final PgImportRule importRule = PgImportRule.create(DataFormat.ACEF, 1, 5, 6);
  
  public int datasetKey(int key, DataFormat format) {
    return importRule.datasetKey(key, format);
  }
  
  Taxon getByName(int datasetKey, Rank rank, String name) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      List<Taxon> taxa = session.getMapper(TaxonMapper.class).listByName(datasetKey, name, rank);
      if (taxa.size() > 1) throw new IllegalStateException("Multiple taxa found for name="+name);
      return taxa.get(0);
    }
  }
  
  int createSector(Sector.Mode mode, Taxon src, Taxon target) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setDatasetKey(src.getDatasetKey());
      sector.setSubject(new SimpleName(src.getId(), src.getName().canonicalNameComplete(), src.getName().getRank()));
      sector.setTarget(new SimpleName(target.getId(), target.getName().canonicalNameComplete(), target.getName().getRank()));
      sector.applyUser(InitMybatisRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector.getKey();
    }
  }
    
  void syncAll() throws InterruptedException {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      for (Sector s : session.getMapper(SectorMapper.class).list(null)) {
        sync(s.getKey());
      }
    }
  }
  
  void sync(int sectorKey) throws InterruptedException {
    SectorSync ss = new SectorSync(sectorKey, PgSetupRule.getSqlSessionFactory(), null,
        SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, InitMybatisRule.TEST_USER);
    ss.run();

    DatasetImportDao diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory());
    diDao.createSuccess(Datasets.DRAFT_COL);
  }
  
  void printDraft() throws Exception {
    print(Datasets.DRAFT_COL);
  }
  
  void print(int datasetKey) throws Exception {
    StringWriter writer = new StringWriter();
    writer.append("\nDATASET "+datasetKey+"\n");
    TextTreePrinter.dataset(datasetKey, PgSetupRule.getSqlSessionFactory(), writer).print();
    System.out.println(writer.toString());
  }

  @Test
  public void test1_5_6() throws Exception {
    print(datasetKey(1, DataFormat.ACEF));
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
    
    Taxon src = getByName(datasetKey(1, DataFormat.ACEF), Rank.ORDER, "Fabales");
    Taxon trg = getByName(Datasets.DRAFT_COL, Rank.SUPERKINGDOM, "Biota");
    createSector(Sector.Mode.ATTACH, src, trg);
  
    src = getByName(datasetKey(5, DataFormat.ACEF), Rank.KINGDOM, "Animalia");
    int s5 = createSector(Sector.Mode.ATTACH, src, trg);
    sync(s5);
  
    src = getByName(datasetKey(6, DataFormat.ACEF), Rank.PHYLUM, "Arthropoda");
    trg = getByName(Datasets.DRAFT_COL, Rank.PHYLUM, "Arthropoda");
    createSector(Sector.Mode.MERGE, src, trg);

    syncAll();
    assertTree("cat1_5_6.txt");
    //printDraft();
  }
 
  void assertTree(String filename) throws IOException {
    InputStream resIn = getClass().getResourceAsStream("/assembly-trees/" + filename);
    String expected = IOUtils.toString(resIn, Charsets.UTF_8).trim();
    
    Writer writer = new StringWriter();
    TextTreePrinter.dataset(Datasets.DRAFT_COL, PgSetupRule.getSqlSessionFactory(), writer).print();
    String tree = writer.toString().trim();
    assertFalse("Empty tree, probably no root node found", tree.isEmpty());
  
    // compare trees
    System.out.println(tree);
    assertEquals("Assembled tree not as expected", expected, tree);
  }
}