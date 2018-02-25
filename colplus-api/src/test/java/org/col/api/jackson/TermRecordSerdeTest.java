package org.col.api.jackson;

import org.col.api.RandomUtils;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class TermRecordSerdeTest {

  @Test
  public void testRoundtrip() throws IOException {
    Random rnd = new Random();
    TermRecord rec = new TermRecord(11, "myFile.txt", DwcTerm.Taxon);
    for (Term t : DwcTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + rnd.nextInt(99)).toLowerCase());
    }
    for (Term t : DcTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + rnd.nextInt(99)));
    }
    for (Term t : GbifTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + rnd.nextInt(99)));
    }
    rec.put(UnknownTerm.build("http://col.plus/terms/punk"), RandomUtils.randomString(1 + rnd.nextInt(99)));

    // to json
    String json = ApiModule.MAPPER.writeValueAsString(rec);
    System.out.println(json);
    TermRecord rec2 = ApiModule.MAPPER.readValue(json, TermRecord.class);
    assertEquals(rec2, rec);
  }
}