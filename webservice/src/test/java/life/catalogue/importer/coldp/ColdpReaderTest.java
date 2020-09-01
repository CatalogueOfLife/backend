package life.catalogue.importer.coldp;

import life.catalogue.api.datapackage.ColdpTerm;
import org.gbif.utils.file.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ColdpReaderTest {

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