package org.col.api.jackson;

import com.google.common.collect.Lists;
import org.col.api.RandomUtils;
import org.col.api.model.ExtendedTermRecord;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 *
 */
@Ignore("UnknownTerm not handled properly")
public class ExtendedTermRecordSerdeTest {

  @Test
  public void testRoundtrip() throws IOException {
    Random rnd = new Random();

    ExtendedTermRecord rec = new ExtendedTermRecord(11, "myFile.txt", DwcTerm.Taxon);

    rec.put(DwcTerm.scientificName, RandomUtils.randomString(1 + rnd.nextInt(99)));
    rec.put(DwcTerm.taxonID, RandomUtils.randomString(1 + rnd.nextInt(99)));
    rec.put(UnknownTerm.build("http://col.plus/terms/punk"), RandomUtils.randomString(1 + rnd.nextInt(99)));

    for (Term rowType : new Term[]{GbifTerm.Distribution, GbifTerm.VernacularName, GbifTerm.TypesAndSpecimen}) {
      String fn = rowType.simpleName() + ".txt";
      List<TermRecord> erecs = Lists.newArrayList();
      rec.getExtensions().put(rowType, erecs);
      for (int i = 1; i < 6; i++) {
        TermRecord erec = new TermRecord(i, fn, rowType);
        erec.put(GbifTerm.canonicalName, RandomUtils.randomString(1 + rnd.nextInt(99)));
        erec.put(AcefTerm.AcceptedTaxonID, RandomUtils.randomString(1 + rnd.nextInt(99)));
        erec.put(DcTerm.title, RandomUtils.randomString(1 + rnd.nextInt(99)));
        erec.put(UnknownTerm.build("http://col.plus/terms/epunk"), RandomUtils.randomString(1 + rnd.nextInt(99)));
        erecs.add(erec);
      }
      // also add one without a file & number
      TermRecord erec = new TermRecord();
      erec.put(DwcTerm.scientificName, RandomUtils.randomString(1 + rnd.nextInt(99)));
      erecs.add(erec);
    }

    // to json
    String json = ApiModule.MAPPER.writeValueAsString(rec);
    //System.out.println(json);
    ExtendedTermRecord rec2 = ApiModule.MAPPER.readValue(json, ExtendedTermRecord.class);
    assertEquals(rec2, rec);

  }
}