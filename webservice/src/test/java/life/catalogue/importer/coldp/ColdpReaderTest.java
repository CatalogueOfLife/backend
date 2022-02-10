package life.catalogue.importer.coldp;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.utils.file.FileUtils;

import java.nio.file.Paths;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ColdpReaderTest {

  @Test
  public void excelExport() throws Exception {
    ColdpReader reader = ColdpReader.from(FileUtils.getClasspathFile("coldp/10").toPath());

    assertEquals(1, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.NameUsage));

    var schema = reader.schema(ColdpTerm.NameUsage).get();
    assertEquals(20, schema.columns.size());
    assertTrue(schema.hasTerm(ColdpTerm.parentID));
  }

  @Test
  public void pluralFilenames() throws Exception {
    ColdpReader reader = ColdpReader.from(FileUtils.getClasspathFile("coldp/1").toPath());
    
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

  @Test
  @Ignore("manual debugging only")
  public void debugArchive() throws Exception {
    ColdpReader reader = ColdpReader.from(Paths.get("/Users/markus/Downloads/ictv"));

    assertEquals(2, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.NameUsage));
    assertTrue(reader.hasSchema(ColdpTerm.NameRelation));
  }
}