package life.catalogue.command;

import life.catalogue.api.model.IndexName;
import life.catalogue.matching.NameIndexMapDBStore;

import life.catalogue.matching.NameIndexStore;

import org.gbif.nameparser.api.Authorship;

import org.junit.Test;
import org.mapdb.DBMaker;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 */
public class NamesIndexCmdTest {

  @Test
  public void buildName() throws Exception {
    var n = NamesIndexCmd.buildName(new String[]{
      "scientific_name","authorship","SPECIES","uninomial","genus","infrageneric_epithet","specific_epithet","infraspecific_epithet","cultivar_epithet",
      "{Müller,Perkins E.I.}","","1999","{Geraldine}","{A.Stephano}","","sanctioning_author",
      "SCIENTIFIC","BOTANICAL","","f","1234","23","idid"
    });
    assertNotNull(n);

    // test kryo
    NameIndexStore store = new NameIndexMapDBStore(DBMaker.memoryDB());
    store.start();
    var idx = new IndexName(n);
    idx.setKey(778899);
    idx.setCanonicalId(778899);
    store.add("asdfgh", idx);
  }

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