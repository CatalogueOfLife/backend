package life.catalogue.importer.acef;

import com.google.common.collect.ImmutableMap;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.common.io.PathUtils;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.utils.file.FileUtils;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 *
 */
public class AcefReaderTest {
  
  @Test
  public void fromFolder() throws Exception {
    AcefReader reader = AcefReader.from(PathUtils.classPathTestRes("acef/0"));
    
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
    AcefReader reader = AcefReader.from(FileUtils.getClasspathFile("acef/corrupt").toPath());
    
    Map<AcefTerm, Integer> expectedRows = ImmutableMap.<AcefTerm, Integer>builder()
        .put(AcefTerm.AcceptedSpecies, 3)
        .put(AcefTerm.AcceptedInfraSpecificTaxa, 0)
        .put(AcefTerm.Synonyms, 0)
        .put(AcefTerm.CommonNames, 0)  // we miss the required column CommonName, hence nothing
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
    
  }
  
  @Test
  public void fromDataFolder() throws Exception {
    AcefReader reader = AcefReader.from(PathUtils.classPathTestRes("acef/0data"));
    
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
  
}