package org.col.importer.coldp;

import org.col.api.datapackage.ColTerm;
import org.gbif.utils.file.FileUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ColdpReaderTest {
  
  
  @Test
  public void pluralFilenames() throws Exception {
    ColdpReader reader = ColdpReader.from(FileUtils.getClasspathFile("coldp/1").toPath());
    
    assertEquals(9, reader.schemas().size());
    assertTrue(reader.hasSchema(ColTerm.Description));
    assertTrue(reader.hasSchema(ColTerm.Distribution));
    assertTrue(reader.hasSchema(ColTerm.Media));
    assertTrue(reader.hasSchema(ColTerm.Name));
    assertTrue(reader.hasSchema(ColTerm.NameRel));
    assertTrue(reader.hasSchema(ColTerm.Reference));
    assertTrue(reader.hasSchema(ColTerm.Synonym));
    assertTrue(reader.hasSchema(ColTerm.Taxon));
    assertTrue(reader.hasSchema(ColTerm.VernacularName));
  }
}