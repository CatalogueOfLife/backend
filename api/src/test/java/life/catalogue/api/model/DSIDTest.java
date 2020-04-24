package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class DSIDTest {
  DSID key;

  @Test
  public void concat() {
    assertEquals("73:1234", DSID.of(73, "1234").concat());
    assertEquals("73:-1Gtsjh ).", DSID.of(73, "-1Gtsjh ).").concat());
    assertEquals("73::", DSID.of(73, ":").concat());
    assertEquals("73:", DSID.of(73, "").concat());
  }

  @Test
  public void parse() {
    assertEquals(DSID.of(73, "1234"), DSID.parseStr("73:1234"));
    assertEquals(DSID.of(73, "-1Gtsjh )."), DSID.parseStr("73:-1Gtsjh )."));
    assertEquals(DSID.of(73, ":"), DSID.parseStr("73::"));
    assertEquals(DSID.of(73, ""), DSID.parseStr("73:"));
    assertEquals(DSID.of(-1, "13:4:15.6"), DSID.parseStr("-1:13:4:15.6"));
  }

}