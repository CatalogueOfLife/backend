package org.col.db.mapper;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.col.api.RandomUtils;
import org.col.api.model.ExtendedTermRecord;
import org.col.api.model.TermRecord;
import org.col.api.model.VerbatimRecord;
import org.col.db.TestEntityGenerator;
import org.gbif.dwc.terms.*;
import org.javers.common.collections.Sets;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class VerbatimRecordMapperTest extends MapperTestBase<VerbatimRecordMapper> {
  Random rnd = new Random();

  public VerbatimRecordMapperTest() {
    super(VerbatimRecordMapper.class);
  }

  private VerbatimRecord create() throws Exception {
    VerbatimRecord v = VerbatimRecord.create();
    v.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    v.setId(RandomUtils.randomString(8));
    v.setTerms(new ExtendedTermRecord());
    // core
    for (DwcTerm t : DwcTerm.values()) {
      if (t.isClass()) continue;
      v.setTerm(t, RandomUtils.randomString(1 + rnd.nextInt(99)).toLowerCase());
    }
    for (GbifTerm t : GbifTerm.values()) {
      if (t.isClass()) continue;
      v.setTerm(t, RandomUtils.randomString(1 + rnd.nextInt(19)).toLowerCase());
    }
    v.setTerm(UnknownTerm.build("http://col.plus/terms/punk"), RandomUtils.randomString(1 + rnd.nextInt(50)));

    // distribution
    for (int idx=0; idx < 2; idx++) {
      TermRecord rec = new TermRecord();
      rec.put(DwcTerm.countryCode, RandomUtils.randomString(2).toUpperCase());
      rec.put(DwcTerm.locality, RandomUtils.randomString(20).toLowerCase());
      rec.put(DwcTerm.occurrenceStatus, "present");
      rec.put(UnknownTerm.build("http://col.plus/terms/punk"), "Stiv Bators");
      v.addExtensionRecord(GbifTerm.Distribution, rec);
    }

    // vernacular
    for (int idx=0; idx < 3; idx++) {
      TermRecord rec = new TermRecord();
      rec.put(DwcTerm.countryCode, RandomUtils.randomString(2).toUpperCase());
      rec.put(DcTerm.language, RandomUtils.randomString(3).toLowerCase());
      rec.put(DwcTerm.vernacularName, RandomUtils.randomSpecies());
      rec.put(UnknownTerm.build("http://col.plus/terms/punk"), "Stiv Bators");
      v.addExtensionRecord(GbifTerm.VernacularName, rec);
    }
    return v;
  }

  @Test
  public void roundtrip() throws Exception {
    VerbatimRecord r1 = create();
    mapper().create(r1, 1, 1, 1);

    commit();

    VerbatimRecord r2 = mapper().getByName(1);

    diff(r1, r2);

    assertEquals(r1, r2);

    VerbatimRecord r3 = mapper().getByTaxon(1);
    assertEquals(r1, r3);

  }

  static void diff(VerbatimRecord v1, VerbatimRecord v2) {
    System.out.println("DIFF core:");
    Set<Map.Entry<Term, String>> diff = Sets.difference(v1.getTerms().termValues(), v2.getTerms().termValues());
    System.out.println(diff);

    System.out.println("DIFF extensions:");
    MapDifference<Term, List<TermRecord>> diff2 = Maps.difference(v1.getExtensions(), v2.getExtensions());
    System.out.println(diff2);

  }
}