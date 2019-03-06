package org.col.admin.assembly;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.admin.importer.PgImportRule;
import org.col.api.model.Sector;
import org.col.api.model.SimpleName;
import org.col.api.vocab.DataFormat;
import org.col.db.PgSetupRule;
import org.col.db.mapper.InitMybatisRule;
import org.col.db.mapper.SectorMapper;
import org.gbif.nameparser.api.Rank;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class DecisionRematcherTest {
  
  @ClassRule
  public static PgSetupRule pg = new PgSetupRule();
  
  @Rule
  public final PgImportRule importRule = PgImportRule.create(DataFormat.ACEF, 0);

  @Test
  public void run() {
    final int datasetKey = importRule.datasetKey(0, DataFormat.ACEF);
    
    int s1 = createSector(Sector.Mode.ATTACH, datasetKey,
        new SimpleName(null, "Superfabaceae", Rank.SUPERFAMILY),
        new SimpleName(null, "Animalia", Rank.KINGDOM)
    );
    int s2 = createSector(Sector.Mode.MERGE, datasetKey,
        new SimpleName(" ", "Astracantha", Rank.GENUS),
        new SimpleName("", "Plantae", Rank.KINGDOM)
    );
  
    DecisionRematcher rem = new DecisionRematcher(PgSetupRule.getSqlSessionFactory(), datasetKey);
    rem.run();
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s1b = sm.get(s1);
      Assert.assertNotNull(StringUtils.trimToNull(s1b.getSubject().getId()));

      Sector s2b = sm.get(s2);
      Assert.assertNotNull(StringUtils.trimToNull(s2b.getSubject().getId()));
    }
  
  }
  
  static int createSector(Sector.Mode mode, int datasetKey, SimpleName src, SimpleName target) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.applyUser(InitMybatisRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector.getKey();
    }
  }
}