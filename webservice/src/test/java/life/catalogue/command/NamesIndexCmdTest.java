package life.catalogue.command;

import org.gbif.nameparser.api.Authorship;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 */
public class NamesIndexCmdTest {

  @Test
  public void bool() throws Exception {
    assertTrue(NamesIndexCmd.bool("t"));
    assertFalse(NamesIndexCmd.bool("f"));
  }

  @Test
  public void authors() throws Exception {
    assertEquals(new Authorship(List.of("Xue", "Dong"),null,null), NamesIndexCmd.authors("{Xue,Dong}", null, null));
    assertEquals(new Authorship(List.of("Vill."),List.of("Mérat"),"1935"), NamesIndexCmd.authors("{Vill.}", "{Mérat}", "1935"));
  }

}