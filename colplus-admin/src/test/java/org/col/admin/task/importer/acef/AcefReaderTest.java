package org.col.admin.task.importer.acef;

import org.col.api.model.TermRecord;
import org.col.api.vocab.AcefTerm;
import org.gbif.utils.file.FileUtils;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class AcefReaderTest {

  @Test
  public void from() throws Exception {
    AcefReader reader = AcefReader.from(FileUtils.getClasspathFile("acef/1"));

    Iterator<TermRecord> iter = reader.iterator(AcefTerm.AcceptedSpecies);
    while (iter.hasNext()) {
      TermRecord tr = iter.next();
      assertNotNull(tr.get(AcefTerm.AcceptedTaxonID));
      assertEquals("Fabales", tr.get(AcefTerm.Order));
      assertEquals("Fabaceae", tr.get(AcefTerm.Family));
      assertNotNull(tr.get(AcefTerm.Genus));
      assertNotNull(tr.get(AcefTerm.SpeciesEpithet));
      assertNotNull(tr.get(AcefTerm.AuthorString));
      assertNotNull(tr.get(AcefTerm.GSDNameStatus));
      assertEquals("Accepted name", tr.get(AcefTerm.Sp2000NameStatus));
      assertEquals("Terrestrial", tr.get(AcefTerm.LifeZone));
      assertNotNull(tr.get(AcefTerm.SpeciesURL));
      assertNotNull(tr.get(AcefTerm.GSDNameGUID));
      if (tr.get(AcefTerm.AcceptedTaxonID).equals("695678")) {
        assertEquals("1", tr.get(AcefTerm.IsExtinct));
        assertEquals("1", tr.get(AcefTerm.HasPreHolocene));
        assertEquals("0", tr.get(AcefTerm.HasModern));
      } else {
        assertEquals("0", tr.get(AcefTerm.IsExtinct));
        assertEquals("0", tr.get(AcefTerm.HasPreHolocene));
        assertEquals("1", tr.get(AcefTerm.HasModern));
      }
    }
  }

}