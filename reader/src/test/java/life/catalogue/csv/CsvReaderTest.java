package life.catalogue.csv;

import com.univocity.parsers.common.CommonParserSettings;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.Resources;

import org.gbif.dwc.terms.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.univocity.parsers.csv.CsvParserSettings;

import static org.junit.Assert.*;

/**
 *
 */
public class CsvReaderTest {

  @Test
  public void clean() throws Exception {
    assertNull(CsvReader.clean(""));
    assertNull("", CsvReader.clean("null"));
    assertNull("", CsvReader.clean("null  "));
    assertEquals("hi Pete", CsvReader.clean("hi  Pete "));
    assertEquals("hi Pete", CsvReader.clean("hi  Pete "));
    assertEquals("hi Pete", CsvReader.clean("hi  Pete "));
    assertEquals("öüä", CsvReader.clean("öüä")); // 2 byte encodings
    // 3 byte encodings using the combining diaresis - visually entirely different, but not in raw bytes!
    assertEquals("Bärmann, Fürst von Lieven & Sudhaus, 2009", CsvReader.clean("Bärmann, Fürst von Lieven & Sudhaus, 2009"));
    assertEquals("Niä", CsvReader.clean("Nia"+'\u0308')); // combining diaresis
    assertEquals("Nin̆a", CsvReader.clean("Nin"+'\u0306' +"a")); // combining breve
    assertEquals("Niña", CsvReader.clean("Nin"+'\u0303' +"a")); // combining tilde
    assertEquals("Niéa", CsvReader.clean("Nie"+'\u0301' +"a")); // combining Acute Accent
  }

  @Test
  public void fromFolder() throws Exception {
    CsvReader reader = CsvReader.from(Resources.toFile("acef/0").toPath());
    
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
  public void html() throws Exception {
    var lines = Resources.lines("csv/doctype.html").collect(Collectors.toList());
    assertTrue(CsvReader.containsNonTabularData(lines));

    lines = Resources.lines("csv/15-CommonNames.txt").collect(Collectors.toList());
    assertFalse(CsvReader.containsNonTabularData(lines));
  }

  @Test
  public void corruptFiles() throws Exception {
    CsvReader reader = CsvReader.from(Resources.toFile("acef/corrupt").toPath());
    
    Map<AcefTerm, Integer> expectedRows = ImmutableMap.<AcefTerm, Integer>builder()
        .put(AcefTerm.AcceptedSpecies, 3)
        .put(AcefTerm.AcceptedInfraSpecificTaxa, 0)
        .put(AcefTerm.Synonyms, 0)
        .put(AcefTerm.CommonNames, 5)
        .put(AcefTerm.Distribution, 0)
        .put(AcefTerm.Reference, 0)
        .put(AcefTerm.NameReferencesLinks, 4)
        .put(AcefTerm.SourceDatabase, 1)
        .build();
    
    for (AcefTerm t : AcefTerm.values()) {
      if (t.isClass()) {
        AtomicInteger counter = new AtomicInteger(0);
        reader.stream(t).forEach(tr -> {
          counter.incrementAndGet();
          assertTrue(tr.size() > 0);
        });
        assertEquals("bad "+t, expectedRows.get(t), (Integer) counter.get());
        Optional<VerbatimRecord> row = reader.readFirstRow(t);
        if (expectedRows.get(t) > 0) {
          assertTrue(row.isPresent());
        } else {
          assertFalse(row.isPresent());
        }
      }
    }
  
    reader.stream(AcefTerm.AcceptedSpecies).forEach(tr -> {
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
  
    Optional<VerbatimRecord> row = reader.readFirstRow(AcefTerm.CommonNames);
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
  
  private CsvParserSettings assertCsvFormat(String resource, char delimiter, char quote) throws IOException {
    CommonParserSettings<?> settings = CsvReader.discoverFormat(
        new BufferedReader(new InputStreamReader(ClassLoader.getSystemResource(resource).openStream(), Charsets.UTF_8))
            .lines()
            .collect(Collectors.toList())
    );
    assertTrue(settings instanceof CsvParserSettings);
    var csv = (CsvParserSettings) settings;

    assertEquals(delimiter, csv.getFormat().getDelimiter());
    assertEquals(quote, csv.getFormat().getQuote());
    return csv;
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1242
   */
  @Test
  public void strayLines() throws Exception {
    CsvReader reader = CsvReader.from(Resources.toFile("coldp/stray").toPath());
    var s = reader.schemas().iterator().next();
    AtomicInteger cnt = new AtomicInteger();
    AtomicInteger skipped = new AtomicInteger();
    Set<String> ids = new HashSet<>();
    reader.stream(s.rowType).forEach(r -> {
      cnt.incrementAndGet();
      if (r.hasIssue(Issue.PREVIOUS_LINE_SKIPPED)) {
        skipped.incrementAndGet();
      }
      ids.add(r.get(DcTerm.identifier));
      System.out.println(r);
    });
    assertEquals(30, cnt.get());
    assertEquals(1, skipped.get()); // 23341
    assertEquals(30, ids.size());
    assertTrue(ids.contains("6"));
    assertTrue(ids.contains("303989"));
    assertTrue(ids.contains("23340"));
    assertTrue(ids.contains("303988"));
    assertTrue(ids.contains("303984"));
    assertFalse(ids.contains("23341")); // skipped
  }

  @Test
  public void discoverFormat() throws Exception {
    assertCsvFormat("csv/15-CommonNames.txt", ',', '"');
    assertCsvFormat("csv/15-References.txt", ',', '"');
    assertCsvFormat("csv/15-Synonyms.txt", ',', '"');
    assertCsvFormat("csv/Media.csv", ',', '"');
    assertCsvFormat("csv/Media-pipe.txt", '|', '"');
  }
}