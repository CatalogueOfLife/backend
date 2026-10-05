package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorProfile;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorMapperTest;

import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SectorProfileDaoTest extends DaoTestBase {
  SectorProfileDao dao;

  @Before
  public void init() {
    dao = new SectorProfileDao(factory(), validator);
  }

  private SectorProfile profile(String title) {
    var p = new SectorProfile();
    p.setDatasetKey(Datasets.COL);
    p.setTitle(title);
    return p;
  }

  @Test
  public void effectiveSettings() {
    Sector s = SectorMapperTest.create();
    s.setMode(Sector.Mode.MERGE);
    s.setRanks(EnumSet.noneOf(Rank.class));
    try (SqlSession session = factory().openSession(true)) {
      session.getMapper(SectorMapper.class).create(s);
    }
    var p = profile("genera only");
    p.getSettings().setRanks(EnumSet.of(Rank.GENUS));
    dao.create(p, Users.TESTER);

    var eff = dao.effectiveSettings(s.getKey());
    assertEquals(EnumSet.of(Rank.GENUS), eff.getSettings().getRanks());
    assertEquals("profile:" + p.getId(), eff.getSources().get("ranks"));
    // the sector itself sets a code in SectorMapperTest.create
    assertEquals(s.getCode(), eff.getSettings().getCode());
    assertEquals("sector", eff.getSources().get("code"));
  }

  @Test(expected = NotFoundException.class)
  public void effectiveSettingsOfMissingSector() {
    dao.effectiveSettings(DSID.of(Datasets.COL, -1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidBlockedNamePatternIsRejected() {
    var p = profile("broken");
    p.getSettings().setBlockedNamePatterns(Set.of("Aus (bus"));
    dao.create(p, Users.TESTER);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidNameFilterIsRejected() {
    var p = profile("broken");
    p.getSettings().setNameFilter("[A-Z");
    dao.create(p, Users.TESTER);
  }
}
