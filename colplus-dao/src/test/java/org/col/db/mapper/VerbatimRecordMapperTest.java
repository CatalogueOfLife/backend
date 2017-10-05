package org.col.db.mapper;

import org.col.api.TermRecord;
import org.col.api.VerbatimRecord;
import org.col.api.VerbatimRecordTerms;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.*;
import org.gbif.utils.text.StringUtils;
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
    v.setDataset(D1);
    v.setId(StringUtils.randomString(8));
    v.addIssue(Issue.ACCEPTED_NAME_MISSING);
    v.addIssue(Issue.HOMONYM, "Abies alba");
    v.setTerms(new VerbatimRecordTerms());
    // core
    for (DwcTerm t : DwcTerm.values()) {
      if (t.isClass()) continue;
      //v.setCoreTerm(t, StringUtils.randomString(1 + rnd.nextInt(99)).toLowerCase());
    }
    for (GbifTerm t : GbifTerm.values()) {
      if (t.isClass()) continue;
      v.setCoreTerm(t, StringUtils.randomString(1 + rnd.nextInt(19)).toLowerCase());
    }
    v.setCoreTerm(new UnknownTerm(URI.create("http://col.plus/terms/punk")), StringUtils.randomString(1 + rnd.nextInt(50)));

    // distribution
    for (int idx=0; idx < 2; idx++) {
      TermRecord rec = new TermRecord();
      rec.put(DwcTerm.countryCode, StringUtils.randomString(2).toUpperCase());
      rec.put(DwcTerm.locality, StringUtils.randomString(20).toLowerCase());
      rec.put(DwcTerm.occurrenceStatus, "present");
      rec.put(new UnknownTerm(URI.create("http://col.plus/terms/punk")), "Stiv Bators");
      v.addExtensionRecord(GbifTerm.Distribution, rec);
    }

    // vernacular
    for (int idx=0; idx < 3; idx++) {
      TermRecord rec = new TermRecord();
      rec.put(DwcTerm.countryCode, StringUtils.randomString(2).toUpperCase());
      rec.put(DcTerm.language, StringUtils.randomString(3).toLowerCase());
      rec.put(DwcTerm.vernacularName, StringUtils.randomSpecies());
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