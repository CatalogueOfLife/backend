package life.catalogue.matching.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchingDaoTest {

  @Test
  void norm() {
    assertEquals("", MatchingDao.norm(null));
    assertEquals("", MatchingDao.norm("   "));
    assertEquals("", MatchingDao.norm(" .-"));
    assertTrue(MatchingDao.norm(null).equals(MatchingDao.norm(".")));
  }
}