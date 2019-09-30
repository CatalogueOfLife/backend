package org.col.importer.coldp;

import org.col.api.datapackage.ColdpTerm;
import org.gbif.utils.file.FileUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ColdpReaderTest {
  
  
  @Test
  public void pluralFilenames() throws Exception {
    ColdpReader reader = ColdpReader.from(FileUtils.getClasspathFile("coldp/1").toPath());
    
    assertEquals(9, reader.schemas().size());
    assertTrue(reader.hasSchema(ColdpTerm.Description));
    assertTrue(reader.hasSchema(ColdpTerm.Distribution));
    assertTrue(reader.hasSchema(ColdpTerm.Media));
    assertTrue(reader.hasSchema(ColdpTerm.Name));
    assertTrue(reader.hasSchema(ColdpTerm.NameRel));
    assertTrue(reader.hasSchema(ColdpTerm.Reference));
    assertTrue(reader.hasSchema(ColdpTerm.Synonym));
    assertTrue(reader.hasSchema(ColdpTerm.Taxon));
    assertTrue(reader.hasSchema(ColdpTerm.VernacularName));
  }
}