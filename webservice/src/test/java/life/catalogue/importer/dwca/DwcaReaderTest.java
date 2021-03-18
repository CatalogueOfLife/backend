package life.catalogue.importer.dwca;

import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.common.io.PathUtils;
import org.gbif.dwc.terms.*;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 *
 */
public class DwcaReaderTest {
  public static final Term TERM_CoL_name = new UnknownTerm(URI.create("http://unknown.org/CoL_name"), false);

  @Test
  public void metaIF() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/0"));
    
    assertEquals(1, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(15, reader.coreSchema().columns.size());
    assertTrue(reader.coreSchema().hasTerm(DwcaTerm.ID));
    
    AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
    });
    assertEquals(3, counter.get());
  }
  
  @Test
  public void dwca1() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/1"));
    
    assertEquals(1, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(7, reader.coreSchema().size());
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.taxonID));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.parentNameUsageID));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.acceptedNameUsageID));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.originalNameUsageID));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.scientificName));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.taxonRank));
    
    AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.taxonID));
      assertNotNull(tr.get(DwcaTerm.ID));
      assertEquals(tr.get(DwcaTerm.ID), tr.get(DwcTerm.taxonID));
      assertNotNull(tr.get(DwcTerm.scientificName));
    });
    assertEquals(21, counter.get());
  }

  /**
   * EEA redlist file with unknown term columns
   */
  @Test
  public void dwca37() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/37"));

    assertEquals(1, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(13, reader.coreSchema().size());
    assertTrue(reader.coreSchema().hasTerm(TERM_CoL_name));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.scientificName));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.taxonRank));
  }

  @Test
  public void dwca6() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/6"));
    
    assertEquals(1, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(15, reader.coreSchema().size());
    assertTrue(reader.coreSchema().hasTerm(DwcaTerm.ID));
    assertFalse(reader.coreSchema().hasTerm(DwcTerm.taxonID));
    
    AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
      assertNotNull(tr.get(DwcaTerm.ID));
    });
    assertEquals(226, counter.get());
    
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.scientificName));
    assertTrue(reader.coreSchema().hasTerm(DwcTerm.taxonRank));
  }
  
  @Test
  public void dwca14() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/14"));
    
    assertEquals(2, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertTrue(reader.coreSchema().hasTerm(DwcaTerm.ID));
    assertFalse(reader.coreSchema().hasTerm(DwcTerm.taxonID));
    assertEquals(19, reader.coreSchema().size());
    
    assertTrue(reader.hasData(DwcTerm.MeasurementOrFact));
    assertTrue(reader.schema(DwcTerm.MeasurementOrFact).get().hasTerm(DwcaTerm.ID));
    assertEquals(9, reader.schema(DwcTerm.MeasurementOrFact).get().size());
    
    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
      assertNotNull(tr.get(DwcaTerm.ID));
    });
    assertEquals(10, counter.get());
    
    counter.set(0);
    reader.stream(DwcTerm.MeasurementOrFact).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaTerm.ID));
      assertNotNull(tr.get(DwcTerm.measurementValue));
    });
    assertEquals(128, counter.get());
  }
  
  @Test
  public void dwca24() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/24"));
    
    assertEquals(5, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(7, reader.coreSchema().size());
    assertTrue(reader.coreSchema().hasTerm(DwcaTerm.ID));
    
    assertTrue(reader.hasData(GbifTerm.Distribution));
    assertEquals(8, reader.schema(GbifTerm.Distribution).get().size());
    
    assertTrue(reader.hasData(GbifTerm.VernacularName));
    assertEquals(6, reader.schema(GbifTerm.VernacularName).get().size());
  
    assertTrue(reader.hasData(GbifTerm.Description));
    assertEquals(5, reader.schema(GbifTerm.Description).get().size());
  
    assertTrue(reader.hasData(GbifTerm.Multimedia));
    assertEquals(7, reader.schema(GbifTerm.Multimedia).get().size());

    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
      assertEquals(tr.get(DwcaTerm.ID), tr.get(DwcTerm.taxonID));
    });
    assertEquals(5, counter.get());
    
    counter.set(0);
    reader.stream(GbifTerm.Distribution).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaTerm.ID));
      assertEquals("present", tr.get(DwcTerm.occurrenceStatus));
    });
    assertEquals(13, counter.get());
    
    counter.set(0);
    final Term countOnMe = VocabularyUtils.TF.findPropertyTerm("http://my.org/dont/count/on/me");
    reader.stream(GbifTerm.VernacularName).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaTerm.ID));
      assertEquals("xyz", tr.get(countOnMe));
    });
    assertEquals(7, counter.get());
  
    counter.set(0);
    reader.stream(GbifTerm.Description).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaTerm.ID));
      assertNotNull(tr.get(DwcTerm.taxonID));
      assertNotNull(tr.get(DcTerm.description));
    });
    assertEquals(3, counter.get());
    
    counter.set(0);
    reader.stream(GbifTerm.Multimedia).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaTerm.ID));
      assertNotNull(tr.get(DwcTerm.taxonID));
      assertNotNull(tr.get(DcTerm.identifier));
    });
    assertEquals(1, counter.get());
  }
  
  /**
   * WoRMS meta.xml
   */
  @Test
  public void dwca25() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/25"));
    
    assertEquals(2, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals('\0', reader.coreSchema().settings.getFormat().getQuote());
    assertEquals(30, reader.coreSchema().size());
    assertTrue(reader.coreSchema().hasTerm(DwcaTerm.ID));
    
    
    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
      assertEquals(tr.get(DwcaTerm.ID), tr.get(DwcTerm.taxonID));
    });
    assertEquals(9, counter.get());
  }
  
  @Test
  @Ignore("manual test to check local archives")
  public void testLocalFile() throws Exception {
    DwcaReader reader = DwcaReader.from(Paths.get("/Users/markus/Downloads/classification.tsv"));
    
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertTrue(reader.coreSchema().hasTerm(DwcaTerm.ID));
    
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      assertNotNull(tr.get(DwcaTerm.ID));
      assertNotNull(tr.get(DwcTerm.taxonID));
      assertNotNull(tr.get(DwcTerm.scientificName));
    });
  }
}