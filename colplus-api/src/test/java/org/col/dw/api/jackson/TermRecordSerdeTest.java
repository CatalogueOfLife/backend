package org.col.dw.api.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.col.dw.api.RandomUtils;
import org.col.dw.api.TermRecord;
import org.gbif.dwc.terms.*;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class TermRecordSerdeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void testRoundtrip() throws IOException {
    Random rnd = new Random();
    TermRecord rec = new TermRecord();
    for (Term t : DwcTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + rnd.nextInt(99)).toLowerCase());
    }
    for (Term t : DcTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + rnd.nextInt(99)));
    }
    for (Term t : GbifTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + rnd.nextInt(99)));
    }
    rec.put(new UnknownTerm(URI.create("http://col.plus/terms/punk")), RandomUtils.randomString(1 + rnd.nextInt(99)));

    // to json
    String json = MAPPER.writeValueAsString(rec);
    TermRecord rec2 = MAPPER.readValue(json, TermRecord.class);
    assertEquals(rec2, rec);
  }
}