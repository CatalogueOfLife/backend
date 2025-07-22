package life.catalogue.importer;

import life.catalogue.db.mapper.DatasetMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousImporterTest {

  @Test
  void wasImportedBefore() throws IOException {
    assertFalse(ContinuousImporter.wasImportedBefore(null, null));

    DatasetMapper.DatasetAttempt d = new DatasetMapper.DatasetAttempt();
    assertFalse(ContinuousImporter.wasImportedBefore(d, null));
    assertFalse(ContinuousImporter.wasImportedBefore(d, LocalDate.MIN));
    assertFalse(ContinuousImporter.wasImportedBefore(d, LocalDate.MAX));

    d.setLastImportAttempt(LocalDateTime.of(2018, Month.OCTOBER, 12, 3, 0));
    assertFalse(ContinuousImporter.wasImportedBefore(d, null));
    assertFalse(ContinuousImporter.wasImportedBefore(d, LocalDate.MIN));
    assertTrue(ContinuousImporter.wasImportedBefore(d, LocalDate.MAX));
    assertFalse(ContinuousImporter.wasImportedBefore(d, LocalDate.of(2018, Month.OCTOBER, 12)));
    assertTrue(ContinuousImporter.wasImportedBefore(d, LocalDate.of(2018, Month.OCTOBER, 13)));
    assertTrue(ContinuousImporter.wasImportedBefore(d, LocalDate.of(2018, Month.NOVEMBER, 12)));
    assertTrue(ContinuousImporter.wasImportedBefore(d, LocalDate.of(2019, Month.NOVEMBER, 12)));
    assertFalse(ContinuousImporter.wasImportedBefore(d, LocalDate.of(2017, Month.NOVEMBER, 12)));
  }
}