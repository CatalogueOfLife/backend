package life.catalogue.api.model;

import life.catalogue.api.vocab.Users;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NameRelationTest {

  @Test
  void isMerged() {
    var nr = new NameRelation();
    assertNull(nr.isMerged());

    nr.setCreatedBy(Users.TESTER);
    assertNull(nr.isMerged());

    nr.setCreatedBy(Users.HOMOTYPIC_GROUPER);
    assertTrue(nr.isMerged());

    nr.setSectorKey(12);
    nr.setSectorMode(Sector.Mode.ATTACH);
    assertTrue(nr.isMerged());

    nr.setCreatedBy(Users.TESTER);
    assertFalse(nr.isMerged());

    nr.setSectorMode(Sector.Mode.MERGE);
    assertTrue(nr.isMerged());
  }
}