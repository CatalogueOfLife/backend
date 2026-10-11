package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class VerbatimRecordTest extends SerdeTestBase<VerbatimRecord> {
  
  private VerbatimRecord v;
  
  public VerbatimRecordTest() {
    super(VerbatimRecord.class);
  }

  @Override
  public VerbatimRecord genTestValue() throws Exception {
    return TestEntityGenerator.createVerbatim();
  }
  
  @Before
  public void init() {
    v = new VerbatimRecord(11, "myFile.txt", DwcTerm.Taxon);
    initRecord(v);
  }

  private static void initRecord(VerbatimRecord rec) {
    rec.put(DwcTerm.scientificName, "Abies alba");
    rec.put(DwcTerm.scientificNameAuthorship, "D&ouml;ring & M&#246;glich");
    rec.put(DwcTerm.verbatimCoordinates, "9-ii-1999\\00°37'55\"S 076°08'39\"W");
    rec.put(DwcTerm.nameAccordingTo, "D\\u00F6ring &amp; M\\u00f6glich");
    rec.put(DwcTerm.namePublishedIn, "D\\u{00F6}ring &amp; M\\u{00f6}glich");
    rec.put(AcefTerm.Title, "A new species of <i>Neamia</i> (Perciformes: Apogonidae) from the West Pacific Ocean.");
    // from is-6157 http://api.col.plus/taxon/7/info
    rec.put(DwcTerm.vernacularName, "&#75;&#101;&#105;&#104;&#228;&#115;&#108;&#117;&#117;&#104;&#97;&#117;&#107;&#105;");
    // from Bob Mesibov
    rec.put(ColdpTerm.scientificName, "Dasysiphonia<U+00A0> japonica");
    rec.put(ColdpTerm.specificEpithet, "Dasysiphonia<U00A0> japonica");
  }
  
  @Test
  public void getTerm() throws Exception {
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Döring & Möglich", v.get(DwcTerm.nameAccordingTo));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));

    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("9-ii-1999\\00°37'55\"S 076°08'39\"W", v.get(DwcTerm.verbatimCoordinates));
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));

    assertEquals("Abies alba", v.get(DwcTerm.scientificName));
    assertEquals("Abies alba", v.getRaw(DwcTerm.scientificName));
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    
    assertEquals("D&ouml;ring & M&#246;glich", v.getRaw(DwcTerm.scientificNameAuthorship));
    assertEquals("Döring & Möglich", v.get(DwcTerm.scientificNameAuthorship));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Döring & Möglich", v.get(DwcTerm.nameAccordingTo));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Döring & Möglich", v.get(DwcTerm.namePublishedIn));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("A new species of Neamia (Perciformes: Apogonidae) from the West Pacific Ocean.", v.get(AcefTerm.Title));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Keihäsluuhauki", v.get(DwcTerm.vernacularName));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
  
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertFalse(v.contains(Issue.INVISIBLE_CHARACTERS));
    assertEquals("Dasysiphonia  japonica", v.get(ColdpTerm.scientificName));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    assertTrue(v.contains(Issue.INVISIBLE_CHARACTERS));

    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Dasysiphonia  japonica", v.get(ColdpTerm.specificEpithet));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    assertTrue(v.contains(Issue.INVISIBLE_CHARACTERS));
  }
  
  @Test
  public void getFirst() throws Exception {
    assertEquals("Abies alba", v.getFirst(GbifTerm.canonicalName, DwcTerm.scientificName));
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    
    assertEquals("Döring & Möglich", v.getFirst(GbifTerm.canonicalName, DwcTerm.scientificNameAuthorship));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Döring & Möglich", v.getFirst(GbifTerm.canonicalName, DwcTerm.nameAccordingTo));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Döring & Möglich", v.getFirst(GbifTerm.canonicalName, DwcTerm.namePublishedIn));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("A new species of Neamia (Perciformes: Apogonidae) from the West Pacific Ocean.", v.getFirst(GbifTerm.canonicalName, AcefTerm.Title));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
    
    init();
    assertFalse(v.contains(Issue.ESCAPED_CHARACTERS));
    assertEquals("Keihäsluuhauki", v.getFirst(GbifTerm.canonicalName, DwcTerm.vernacularName));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));
  }

  /**
   * https://github.com/CatalogueOfLife/data/issues/1765
   */
  @Test
  public void invisibleCharacters() throws Exception {
    // a byte order mark in front of a genus and a soft hyphen inside an epithet
    v = new VerbatimRecord(11, "myFile.txt", ColdpTerm.Name);
    v.put(ColdpTerm.genus, "﻿Janbinmorpha");
    v.put(ColdpTerm.specificEpithet, "inter­media");
    assertEquals("Janbinmorpha", v.get(ColdpTerm.genus));
    assertTrue(v.contains(Issue.INVISIBLE_CHARACTERS));
    assertEquals("intermedia", v.get(ColdpTerm.specificEpithet));

    // a soft hyphen in front of a capital joins a double surname
    v.put(ColdpTerm.authorship, "(Miranda\u00ADRibeiro) Miranda-Ribeiro");
    assertEquals("(Miranda-Ribeiro) Miranda-Ribeiro", v.get(ColdpTerm.authorship));
  }

  @Test
  public void selfClosingTags() throws Exception {
    v = new VerbatimRecord(11, "myFile.txt", ColdpTerm.Name);
    v.put(ColdpTerm.authorship, "Kashani & Ajorloo <i>in<i/> Kashani, Azizi & Ajorloo, 2025");
    assertEquals("Kashani & Ajorloo in Kashani, Azizi & Ajorloo, 2025", v.get(ColdpTerm.authorship));
    assertTrue(v.contains(Issue.ESCAPED_CHARACTERS));

    v.put(ColdpTerm.remarks, "first line<br/>second line<br />third");
    assertEquals("first linesecond linethird", v.get(ColdpTerm.remarks));
  }

  @Test
  public void diacriticsAreNoHomoglyphs() throws Exception {
    v = new VerbatimRecord(11, "myFile.txt", ColdpTerm.Name);
    // a cyrillic Es is a homoglyph, not a diacritic
    v.put(ColdpTerm.authorship, "(Talbot, 1934) Caballero y С. & Vogelsang, 1947");
    v.get(ColdpTerm.authorship);
    assertTrue(v.contains(Issue.HOMOGLYPH_CHARACTERS));
    assertFalse(v.contains(Issue.DIACRITIC_CHARACTERS));

    // a middle dot is a standalone diacritic
    v = new VerbatimRecord(11, "myFile.txt", ColdpTerm.Name);
    v.put(ColdpTerm.authorship, "(Bychowskaja·Pawlowskaja, 1953) Khotenovsky, 1961");
    v.get(ColdpTerm.authorship);
    assertTrue(v.contains(Issue.DIACRITIC_CHARACTERS));
  }

}