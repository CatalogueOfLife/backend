package org.col.admin.task.importer.neo.model;

import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class UnescapedVerbatimRecordTest {

  private UnescapedVerbatimRecord v;

  @Before
  public void init() {
    v = UnescapedVerbatimRecord.create();
    initRecord(v.getTerms());

    v.addExtensionRecord(GbifTerm.VernacularName, initRecord(new TermRecord()));
  }

  private static <T extends TermRecord> T initRecord(T rec) {
    rec.put(DwcTerm.scientificName, "Abies alba");
    rec.put(DwcTerm.scientificNameAuthorship, "D&ouml;ring & M&#246;glich");
    rec.put(DwcTerm.nameAccordingTo, "D\\u00f6ring &amp; M\\366glich");
    rec.put(DwcTerm.namePublishedIn, "D\\u{00f6}ring &amp; M\\366glich");
    rec.put(AcefTerm.Title, "A new species of <i>Neamia</i> (Perciformes: Apogonidae) from the West Pacific Ocean.");
    // from is-6157 http://api.col.plus/taxon/7/info
    rec.put(DwcTerm.vernacularName, "&#75;&#101;&#105;&#104;&#228;&#115;&#108;&#117;&#117;&#104;&#97;&#117;&#107;&#105;");
    return rec;
  }

  @Test
  public void getTerm() throws Exception {
    assertEquals("Abies alba", v.getTerm(DwcTerm.scientificName));
    assertEquals("Abies alba", v.getTermRaw(DwcTerm.scientificName));
    assertFalse(v.isModified());

    assertEquals("D&ouml;ring & M&#246;glich", v.getTermRaw(DwcTerm.scientificNameAuthorship));
    assertEquals("Döring & Möglich", v.getTerm(DwcTerm.scientificNameAuthorship));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("Döring & Möglich", v.getTerm(DwcTerm.nameAccordingTo));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("Döring & Möglich", v.getTerm(DwcTerm.namePublishedIn));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("A new species of Neamia (Perciformes: Apogonidae) from the West Pacific Ocean.", v.getTerm(AcefTerm.Title));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("Keihäsluuhauki", v.getTerm(DwcTerm.vernacularName));
    assertTrue(v.isModified());
  }

  @Test
  public void getFirst() throws Exception {
    assertEquals("Abies alba", v.getFirst(GbifTerm.canonicalName, DwcTerm.scientificName));
    assertFalse(v.isModified());

    assertEquals("Döring & Möglich", v.getFirst(GbifTerm.canonicalName, DwcTerm.scientificNameAuthorship));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("Döring & Möglich", v.getFirst(GbifTerm.canonicalName, DwcTerm.nameAccordingTo));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("Döring & Möglich", v.getFirst(GbifTerm.canonicalName, DwcTerm.namePublishedIn));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("A new species of Neamia (Perciformes: Apogonidae) from the West Pacific Ocean.", v.getFirst(GbifTerm.canonicalName, AcefTerm.Title));
    assertTrue(v.isModified());

    init();
    assertFalse(v.isModified());
    assertEquals("Keihäsluuhauki", v.getFirst(GbifTerm.canonicalName, DwcTerm.vernacularName));
    assertTrue(v.isModified());
  }

  @Test
  public void getExtensionRecords() throws Exception {
    assertFalse(v.isModified());
    for (TermRecord rec : this.v.getExtensionRecords(GbifTerm.VernacularName)) {
      // we preunescape the extension record, so its modified already!
      assertTrue(this.v.isModified());
      assertEquals("Abies alba", rec.get(DwcTerm.scientificName));
      assertEquals("Döring & Möglich", rec.get(DwcTerm.scientificNameAuthorship));
      assertEquals("Döring & Möglich", rec.get(DwcTerm.nameAccordingTo));
      assertEquals("Döring & Möglich", rec.get(DwcTerm.namePublishedIn));
      assertEquals("A new species of Neamia (Perciformes: Apogonidae) from the West Pacific Ocean.", rec.get(AcefTerm.Title));
      assertEquals("Keihäsluuhauki", rec.get(DwcTerm.vernacularName));
      assertTrue(this.v.isModified());
    }
  }

}