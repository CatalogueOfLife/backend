package org.col.admin.task.importer.acef;

import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.utils.file.FileUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class AcefReaderTest {

  @Test
  public void fromFolder() throws Exception {
    AcefReader reader = AcefReader.from(FileUtils.getClasspathFile("acef/0"));

    int counter = 0;
    for (TermRecord tr : reader.read(AcefTerm.ACCEPTED_SPECIES)){
      counter++;
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
      assertEquals("0", tr.get(AcefTerm.IsExtinct));
      assertEquals("0", tr.get(AcefTerm.HasPreHolocene));
      assertEquals("1", tr.get(AcefTerm.HasModern));
    }
    assertEquals(3, counter);
  }

}