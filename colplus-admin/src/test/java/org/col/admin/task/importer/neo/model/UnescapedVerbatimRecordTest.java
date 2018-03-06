package org.col.admin.task.importer.neo.model;

import org.gbif.dwc.terms.DwcTerm;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class UnescapedVerbatimRecordTest {

  private UnescapedVerbatimRecord v;

  @Before
  public void init() {
    v = UnescapedVerbatimRecord.create();
    v.setTerm(DwcTerm.scientificName, "Abies alba");
    v.setTerm(DwcTerm.scientificNameAuthorship, "D&ouml;ring & M&#246;glich");
    v.setTerm(DwcTerm.nameAccordingTo, "D\\u00f6ring &amp; M\\366glich");
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
    assertEquals("D\\u00f6ring &amp; M\\366glich", v.getTermRaw(DwcTerm.nameAccordingTo));
    assertEquals("Döring & Möglich", v.getTerm(DwcTerm.nameAccordingTo));
    assertTrue(v.isModified());
  }

  @Test
  public void getFirst() throws Exception {
  }

  @Test
  public void getExtensionRecords() throws Exception {
  }

}