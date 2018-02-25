package org.col.api.jackson;

import com.google.common.collect.Lists;
import org.col.api.RandomUtils;
import org.col.api.model.ExtendedTermRecord;
import org.col.api.model.TermRecord;
import org.col.api.model.VerbatimRecord;
import org.gbif.dwc.terms.*;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class VerbatimRecordSerdeTest {

  @Test
  public void testRoundtrip() throws IOException {
    Random rnd = new Random();

    VerbatimRecord v = new VerbatimRecord();
    v.setId("12345678");
    v.setDatasetKey(31);
    ExtendedTermRecord rec = new ExtendedTermRecord(11, "myFile.txt", DwcTerm.Taxon);
    v.setTerms(rec);

    rec.put(DwcTerm.scientificName, RandomUtils.randomString(1 + rnd.nextInt(99)));
    rec.put(DwcTerm.taxonID, RandomUtils.randomString(1 + rnd.nextInt(99)));
    rec.put(UnknownTerm.build("http://col.plus/terms/punk"), RandomUtils.randomString(1 + rnd.nextInt(99)));

    for (Term rowType : new Term[]{GbifTerm.Distribution, GbifTerm.VernacularName, GbifTerm.TypesAndSpecimen}) {
      String fn = rowType.simpleName() + ".txt";
      List<TermRecord> erecs = Lists.newArrayList();
      rec.getExtensions().put(rowType, erecs);
      for (int i = 0; i < 4; i++) {
        TermRecord erec = new TermRecord(11, fn, rowType);
        erec.put(GbifTerm.canonicalName, RandomUtils.randomString(1 + rnd.nextInt(99)));
        erec.put(AcefTerm.AcceptedSpecies, RandomUtils.randomString(1 + rnd.nextInt(99)));
        erec.put(DcTerm.title, RandomUtils.randomString(1 + rnd.nextInt(99)));
        erec.put(UnknownTerm.build("http://col.plus/terms/epunk"), RandomUtils.randomString(1 + rnd.nextInt(99)));
        erecs.add(erec);
      }
    }

    // to json
    String json = ApiModule.MAPPER.writeValueAsString(v);
    System.out.println(json);
    VerbatimRecord v2 = ApiModule.MAPPER.readValue(json, VerbatimRecord.class);
    assertEquals(v2, v);

  }
}