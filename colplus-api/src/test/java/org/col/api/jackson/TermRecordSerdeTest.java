package org.col.api.jackson;

import org.col.api.RandomUtils;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.*;
import org.junit.Ignore;

import java.util.Random;

/**
 *
 */
@Ignore("UnknownTerm not handled properly")
public class TermRecordSerdeTest extends SerdeTestBase<TermRecord> {

  public TermRecordSerdeTest() {
    super(TermRecord.class);
  }

  @Override
  public TermRecord genTestValue() throws Exception {
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
    return rec;
  }
}