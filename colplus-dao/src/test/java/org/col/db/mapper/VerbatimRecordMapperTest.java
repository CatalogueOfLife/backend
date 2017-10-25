package org.col.db.mapper;

import org.col.api.RandomUtils;
import org.col.api.TermRecord;
import org.col.api.VerbatimRecord;
import org.col.api.VerbatimRecordTerms;
import org.col.dao.DaoTestUtil;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.UnknownTerm;
import org.junit.Test;

import java.net.URI;
import java.util.Random;

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
    VerbatimRecord v = new VerbatimRecord();
    v.setDataset(DaoTestUtil.DATASET1);
    v.setId(RandomUtils.randomString(8));
    v.setTerms(new VerbatimRecordTerms());
    // core
    for (DwcTerm t : DwcTerm.values()) {
      if (t.isClass()) continue;
      //v.setCoreTerm(t, StringUtils.randomString(1 + rnd.nextInt(99)).toLowerCase());
    }
    for (GbifTerm t : GbifTerm.values()) {
      if (t.isClass()) continue;
      v.setCoreTerm(t, RandomUtils.randomString(1 + rnd.nextInt(19)).toLowerCase());
    }
    v.setCoreTerm(new UnknownTerm(URI.create("http://col.plus/terms/punk")), RandomUtils.randomString(1 + rnd.nextInt(50)));

    // distribution
    for (int idx=0; idx < 2; idx++) {
      TermRecord rec = new TermRecord();
      rec.put(DwcTerm.countryCode, RandomUtils.randomString(2).toUpperCase());
      rec.put(DwcTerm.locality, RandomUtils.randomString(20).toLowerCase());
      rec.put(DwcTerm.occurrenceStatus, "present");
      rec.put(new UnknownTerm(URI.create("http://col.plus/terms/punk")), "Stiv Bators");
      v.addExtensionRecord(GbifTerm.Distribution, rec);
    }

    // vernacular
    for (int idx=0; idx < 3; idx++) {
      TermRecord rec = new TermRecord();
      rec.put(DwcTerm.countryCode, RandomUtils.randomString(2).toUpperCase());
      rec.put(DcTerm.language, RandomUtils.randomString(3).toLowerCase());
      rec.put(DwcTerm.vernacularName, RandomUtils.randomSpecies());
      rec.put(new UnknownTerm(URI.create("http://col.plus/terms/punk")), "Stiv Bators");
      v.addExtensionRecord(GbifTerm.VernacularName, rec);
    }
    return v;
  }

  @Test
  public void roundtrip() throws Exception {
    VerbatimRecord r1 = create();
    mapper().create(r1, 1, 1);

    commit();

    VerbatimRecord r2 = mapper().getByName(r1.getDataset().getKey(), "name-1");
    assertEquals(r1, r2);

    VerbatimRecord r3 = mapper().getByTaxon(r1.getDataset().getKey(), "root-1");
    assertEquals(r1, r3);

  }

}