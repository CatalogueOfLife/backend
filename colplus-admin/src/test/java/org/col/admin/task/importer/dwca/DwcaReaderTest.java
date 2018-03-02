package org.col.admin.task.importer.dwca;

import org.col.api.vocab.VocabularyUtils;
import org.col.util.io.PathUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 *
 */
public class DwcaReaderTest {
  @Test
  public void metaIF() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/0"));

    assertEquals(1, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(17, reader.coreSchema().columns.size());
    assertTrue(reader.coreSchema().columns.contains(DwcaReader.DWCA_ID));

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
    assertEquals(6, reader.coreSchema().columns.size());
    assertTrue(reader.coreSchema().columns.contains(DwcTerm.taxonID));
    assertTrue(reader.coreSchema().columns.contains(DwcTerm.parentNameUsageID));
    assertTrue(reader.coreSchema().columns.contains(DwcTerm.acceptedNameUsageID));
    assertTrue(reader.coreSchema().columns.contains(DwcTerm.originalNameUsageID));
    assertTrue(reader.coreSchema().columns.contains(DwcTerm.scientificName));
    assertTrue(reader.coreSchema().columns.contains(DwcTerm.taxonRank));

    AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.taxonID));
      assertNotNull(tr.get(DwcaReader.DWCA_ID));
      assertEquals(tr.get(DwcaReader.DWCA_ID), tr.get(DwcTerm.taxonID));
      assertNotNull(tr.get(DwcTerm.scientificName));
    });
    assertEquals(21, counter.get());
  }

  @Test
  public void dwca6() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/6"));

    assertEquals(1, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(17, reader.coreSchema().columns.size());
    assertTrue(reader.coreSchema().columns.contains(DwcaReader.DWCA_ID));

    AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
      assertEquals(tr.get(DwcaReader.DWCA_ID), tr.get(DwcTerm.taxonID));
    });
    assertEquals(226, counter.get());

    assertTrue(reader.coreSchema().columns.contains(DwcTerm.scientificName));
    assertTrue(reader.coreSchema().columns.contains(DwcTerm.taxonRank));
  }

  @Test
  public void dwca14() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/14"));

    assertEquals(2, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertTrue(reader.hasData(DwcTerm.MeasurementOrFact));
    assertEquals(19, reader.coreSchema().columns.size());
    assertTrue(reader.coreSchema().columns.contains(DwcaReader.DWCA_ID));
    assertEquals(9, reader.schema(DwcTerm.MeasurementOrFact).get().columns.size());

    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
      assertEquals(tr.get(DwcaReader.DWCA_ID), tr.get(DwcTerm.taxonID));
    });
    assertEquals(10, counter.get());

    counter.set(0);
    reader.stream(DwcTerm.MeasurementOrFact).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaReader.DWCA_ID));
      assertNotNull(tr.get(DwcTerm.measurementValue));
    });
    assertEquals(128, counter.get());
  }

  @Test
  public void dwca24() throws Exception {
    DwcaReader reader = DwcaReader.from(PathUtils.classPathTestRes("dwca/24"));

    assertEquals(3, reader.size());
    assertEquals(DwcTerm.Taxon, reader.coreRowType());
    assertEquals(6, reader.coreSchema().columns.size());
    assertTrue(reader.coreSchema().columns.contains(DwcaReader.DWCA_ID));

    assertTrue(reader.hasData(GbifTerm.Distribution));
    assertEquals(7, reader.schema(GbifTerm.Distribution).get().columns.size());

    assertTrue(reader.hasData(GbifTerm.VernacularName));
    assertEquals(4, reader.schema(GbifTerm.VernacularName).get().columns.size());

    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(DwcTerm.Taxon).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcTerm.scientificName));
      assertEquals(tr.get(DwcaReader.DWCA_ID), tr.get(DwcTerm.taxonID));
    });
    assertEquals(4, counter.get());

    counter.set(0);
    reader.stream(GbifTerm.Distribution).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaReader.DWCA_ID));
      assertEquals("present", tr.get(DwcTerm.occurrenceStatus));
    });
    assertEquals(13, counter.get());

    counter.set(0);
    final Term countOnMe = VocabularyUtils.TF.findPropertyTerm("http://my.org/dont/count/on/me");
    reader.stream(GbifTerm.VernacularName).forEach(tr -> {
      counter.incrementAndGet();
      assertNotNull(tr.get(DwcaReader.DWCA_ID));
      assertEquals("xyz", tr.get(countOnMe));
    });
    assertEquals(7, counter.get());
  }

}