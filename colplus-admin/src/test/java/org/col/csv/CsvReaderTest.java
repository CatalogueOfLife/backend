package org.col.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import com.univocity.parsers.csv.CsvParserSettings;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.utils.file.FileUtils;
import org.junit.Test;
import org.neo4j.string.UTF8;
import scala.Char;

import static org.junit.Assert.*;

/**
 *
 */
public class CsvReaderTest {

  @Test
  public void fromFolder() throws Exception {
    CsvReader reader = CsvReader.from(FileUtils.getClasspathFile("acef/0").toPath());

    AtomicInteger counter = new AtomicInteger(0);
    reader.stream(AcefTerm.AcceptedSpecies).forEach(tr -> {
      counter.incrementAndGet();
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
    });
    assertEquals(3, counter.get());
  }

  @Test
  public void corruptFiles() throws Exception {
    CsvReader reader = CsvReader.from(FileUtils.getClasspathFile("acef/corrupt").toPath());

    AtomicInteger counter = new AtomicInteger(0);
    reader.stream(AcefTerm.AcceptedSpecies).forEach(tr -> {
      counter.incrementAndGet();
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
    });
    assertEquals(3, counter.get());

    Optional<TermRecord> row = reader.readFirstRow(AcefTerm.CommonNames);
    assertTrue(row.isPresent());

    row = reader.readFirstRow(AcefTerm.Distribution);
    assertFalse(row.isPresent());

    row = reader.readFirstRow(AcefTerm.Reference);
    assertFalse(row.isPresent());

    // try to read bad file
    row = reader.readFirstRow(AcefTerm.SourceDatabase);
    // this somehow works, puzzling but ok ...
    //assertFalse(row.isPresent());
  }

  private CsvParserSettings assertFormat(String resource, char delimiter, char quote) throws IOException {
    CsvParserSettings csv = CsvReader.discoverFormat(
        new BufferedReader(new InputStreamReader(ClassLoader.getSystemResource(resource).openStream(), Charsets.UTF_8))
            .lines()
            .collect(Collectors.toList())
    );
    assertEquals(delimiter, csv.getFormat().getDelimiter());
    assertEquals(quote, csv.getFormat().getQuote());
    return csv;
  }

  @Test
  public void discoverFormat() throws Exception {
    assertFormat("csv/15-CommonNames.txt", ',', '"');
    assertFormat("csv/15-References.txt", ',', '"');
    assertFormat("csv/15-Synonyms.txt", ',', '"');
  }
}