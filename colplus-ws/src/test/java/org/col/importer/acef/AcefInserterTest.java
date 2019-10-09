package org.col.importer.acef;

import java.io.IOException;
import java.nio.file.Path;

import com.google.common.collect.Lists;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetType;
import org.col.img.ImageService;
import org.col.importer.InserterBaseTest;
import org.col.importer.NeoInserter;
import org.col.importer.reference.ReferenceFactory;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class AcefInserterTest extends InserterBaseTest {
  
  @Override
  public NeoInserter newInserter(Path resource) throws IOException  {
    return new AcefInserter(store, resource, new ReferenceFactory(store), ImageService.passThru());
  }
  
  @Test
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/acef/0");
    Dataset d = ins.readMetadata().get();
    
    assertEquals(DatasetType.TAXONOMIC, d.getType());
    assertEquals(DataFormat.ACEF, d.getDataFormat());
    assertEquals("ILDIS World", d.getTitle());
    assertEquals("ILDIS", d.getAlias());
    assertEquals("12, May 2014", d.getVersion());
    assertNotNull(d.getReleased());
    //assertEquals("2014-05-05", d.getReleased().toString());
    assertEquals(1, d.getAuthorsAndEditors().size());
    assertThat(d.getAuthorsAndEditors(), is(Lists.newArrayList("Roskov Y.R.")));
    assertEquals("Legumes", d.getGroup());
    assertEquals("The International Legume Database & Information Service (ILDIS) is an international project which aims to document and catalogue the world's legume species diversity in a readily accessible form. Research groups in many countries are participating on a co-operative basis to pool information in the ILDIS World Database of Legumes, which is used to provide a worldwide information service through publications, electronic access and enquiry services.", d.getDescription());
    assertThat(d.getOrganisations(), is(Lists.newArrayList("International")));
    assertEquals("http://www.ildis.org", d.getWebsite().toString());
    assertEquals((Integer)96, d.getCompleteness());
    assertEquals((Integer)4, d.getConfidence());
    assertEquals("YR Roskov & JL", d.getContact());

    assertNull(d.getLicense());
    assertEquals("http://ILDIS.gif", d.getLogo().toString());
    assertNull(d.getCitation());
    assertNull(d.getCode());
  }
  
  @Test
  public void readMetadataBadType() throws Exception {
    NeoInserter ins = setup("/acef/16");
    Dataset d = ins.readMetadata().get();
    
    assertNull(d.getType());
  }
  
}