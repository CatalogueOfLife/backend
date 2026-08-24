package life.catalogue.csv;

import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.Resources;

import org.gbif.dwc.terms.Term;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.*;

public class ColdpReaderTest {

  /**
   * A COLDP archive may consist of nothing but the combined NameUsage file.
   * This one has a row that omits its trailing empty column, which used to disqualify the TSV format
   * during delimiter detection. The reader then fell back to a single column CSV and rejected the whole
   * archive with "One of col:Name, col:NameUsage, col:Reference files required but all are missing".
   */
  @Test
  public void singleNameUsageFile() throws Exception {
    ColdpReader reader = ColdpReader.from(Resources.toFile("coldp/nameusage").toPath());

    assertEquals(1, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.NameUsage));
    Schema s = reader.schema(ColdpTerm.NameUsage).get();
    assertTrue("the tab delimiter must be detected", s.isTsv());
    assertEquals(6, s.columns.size());

    assertEquals(13, reader.stream(ColdpTerm.NameUsage).count());

    // sg1 is the row that omits its trailing authorship column - it must read fine, with a null authorship
    final AtomicBoolean found = new AtomicBoolean(false);
    reader.stream(ColdpTerm.NameUsage).forEach(rec -> {
      if ("sg1".equals(rec.get(ColdpTerm.ID))) {
        found.set(true);
        assertEquals("subgenus", rec.get(ColdpTerm.rank));
        assertEquals("Chaetocnema (Chaetocnema)", rec.get(ColdpTerm.scientificName));
        assertNull(rec.get(ColdpTerm.authorship));
      }
    });
    assertTrue("the row with a missing trailing column must be read", found.get());
  }

  /**
   * https://github.com/CatalogueOfLife/testing/issues/39#issuecomment-1267383797
   */
  @Test
  public void quotesInTsv() throws Exception {
    ColdpReader reader = ColdpReader.from(Resources.toFile("coldp/worms").toPath());

    assertEquals(2, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.Name));
    assertTrue(reader.hasSchema(ColdpTerm.Reference));

    for (Schema s : reader.schemas()) {
      assertTrue(s.isTsv());
      assertNull(s.settings.getNullValue());
    }

    // names
    final AtomicBoolean found = new AtomicBoolean(false);
    reader.stream(ColdpTerm.Name).forEach(rec -> {
      if (rec.getRaw(ColdpTerm.ID).equals("urn:lsid:marinespecies.org:taxname:452250")) {
        found.set(true);
        assertEquals("described as \"Cancer jumpibus, new genus\" which is obviously incorrect", rec.get(ColdpTerm.remarks));
        assertEquals("described as \"<i>Cancer jumpibus</i>, new genus\" which is obviously incorrect", rec.getRaw(ColdpTerm.remarks));
      }
    });
    assertTrue(found.get());

    // references
    found.set(false);
    reader.stream(ColdpTerm.Reference).forEach(rec -> {
      if (rec.getRaw(ColdpTerm.ID).equals("261114")) {
        found.set(true);
        assertEquals("Mantelatto, F.L.; Robles, R.; Schubart, C.D.; Felder, D.L. (2009). \"Molecular phylogeny of the genus Cronius Stimpson, 1860, with reassignment of C. tumidulus and several American species of Portunus to the genus Achelous De Haan, 1833 (Brachyura: Portunidae). In: Crustacean Issues 18: Decapod Crustacean Phylogenetics, Martin, J.W., Crandall, K.A. & Felder, D.L. (eds). CRC Press, England, pp. 567–579.", rec.get(ColdpTerm.citation));
        assertEquals("Mantelatto, F.L.; Robles, R.; Schubart, C.D.; Felder, D.L.", rec.getRaw(ColdpTerm.author));
        assertEquals("\"Molecular phylogeny of the genus <em>Cronius</em> Stimpson, 1860, with reassignment of <em>C. tumidulus</em> and several American species of <em>Portunus</em> to the genus <em>Achelous</em> De Haan, 1833 (Brachyura: Portunidae).", rec.getRaw(ColdpTerm.title));
        assertNull(rec.getRaw(ColdpTerm.remarks));
        assertEquals("https://www.marinespecies.org/aphia.php?p=sourcedetails&id=261114", rec.getRaw(ColdpTerm.link));
        assertEquals("In: Crustacean Issues 18: Decapod Crustacean Phylogenetics, Martin, J.W., Crandall, K.A. & Felder, D.L. (eds)", rec.getRaw(ColdpTerm.containerTitle)); // source
        assertEquals("2009", rec.getRaw(ColdpTerm.issued)); // year
      }
    });
    assertTrue(found.get());


  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1259
   */
  @Test
  public void x00() throws Exception {
    ColdpReader reader = ColdpReader.from(Resources.toFile("coldp/x00").toPath());

    assertEquals(1, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.Name));

    Pattern x0Pattern = Pattern.compile("\\x00");
    reader.stream(ColdpTerm.Name).forEach(rec -> {
      for (Term term : rec.terms()) {
        String val = rec.get(term);
        System.out.println(val);
        assertFalse(x0Pattern.matcher(val).find());
      }
    });
  }

  @Test
  public void defaultValues() throws Exception {
    ColdpReader reader = ColdpReader.from(Resources.toFile("coldp/1").toPath());

    var schema = reader.schema(ColdpTerm.Distribution).get();
    assertTrue(schema.hasTerm(ColdpTerm.gazetteer));
  }

  @Test
  public void excelExport() throws Exception {
    ColdpReader reader = ColdpReader.from(Resources.toFile("coldp/10").toPath());

    assertEquals(1, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.NameUsage));

    var schema = reader.schema(ColdpTerm.NameUsage).get();
    assertEquals(20, schema.columns.size());
    assertTrue(schema.hasTerm(ColdpTerm.parentID));
  }

  @Test
  public void pluralFilenames() throws Exception {
    ColdpReader reader = ColdpReader.from(Resources.toFile("coldp/1").toPath());

    assertEquals(8, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.Distribution));
    assertTrue(reader.hasSchema(ColdpTerm.Media));
    assertTrue(reader.hasSchema(ColdpTerm.Name));
    assertTrue(reader.hasSchema(ColdpTerm.NameRelation));
    assertTrue(reader.hasSchema(ColdpTerm.Reference));
    assertTrue(reader.hasSchema(ColdpTerm.Synonym));
    assertTrue(reader.hasSchema(ColdpTerm.Taxon));
    assertTrue(reader.hasSchema(ColdpTerm.VernacularName));
  }

}