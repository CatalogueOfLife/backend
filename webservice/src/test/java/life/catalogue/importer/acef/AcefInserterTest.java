package life.catalogue.importer.acef;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class AcefInserterTest extends InserterBaseTest {
  
  @Override
  public NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException  {
    return new AcefInserter(store, resource, settings, refFactory);
  }

  @Test
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/acef/0");
    DatasetWithSettings d = ins.readMetadata().get();
    
    assertEquals(DatasetType.TAXONOMIC, d.getType());
    assertEquals(DataFormat.ACEF, d.getDataFormat());
    assertEquals("ILDIS World", d.getTitle());
    assertEquals("ILDIS", d.getAlias());
    assertEquals("12, May 2014", d.getVersion());
    assertNotNull(d.getIssued());
    //assertEquals("2014-05-05", d.getReleased().toString());
    assertEquals(1, d.getCreator().size());
    assertEquals(Agent.parse("YR Roskov & JL"), d.getContact());
    assertThat(d.getCreator(), is(List.of(Agent.parse("Roskov Y.R."))));
    assertEquals("Legumes", d.getTaxonomicScope());
    assertEquals("The International Legume Database & Information Service (ILDIS) is an international project which aims to document and catalogue the world's legume species diversity in a readily accessible form. Research groups in many countries are participating on a co-operative basis to pool information in the ILDIS World Database of Legumes, which is used to provide a worldwide information service through publications, electronic access and enquiry services.", d.getDescription());
    assertThat(d.getContributor(), is(List.of(Agent.parse("International"))));
    assertEquals("http://www.ildis.org", d.getUrl().toString());
    assertEquals((Integer)96, d.getCompleteness());
    assertEquals((Integer)4, d.getConfidence());

    assertNull(d.getLicense());
    assertEquals("http://ILDIS.gif", d.getLogo().toString());
    assertNull(d.getCode());
  }
  
  @Test
  public void readMetadataBadType() throws Exception {
    NeoInserter ins = setup("/acef/16");
    DatasetWithSettings d = ins.readMetadata().get();
    
    assertNull(d.getType());
  }
  
}