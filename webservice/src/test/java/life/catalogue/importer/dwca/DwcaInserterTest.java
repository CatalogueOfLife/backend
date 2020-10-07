package life.catalogue.importer.dwca;

import com.google.common.collect.Lists;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.reference.ReferenceFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.nio.file.Path;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class DwcaInserterTest extends InserterBaseTest {
  
  @Override
  public NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException  {
    return new DwcaInserter(store, resource, settings, new ReferenceFactory(store));
  }
  /**
   * EEA redlist file with unknown term columns
   */
  @Test
  public void dwca37() throws Exception {
    NeoInserter ins = setup("/dwca/37");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = store.usages().objByID("319088");
      assertNotNull(u.getVerbatimKey());
      VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
      v.hasTerm(DwcaReaderTest.TERM_CoL_name);
    }
  }

  @Test
  @Ignore
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/acef/0");
    DatasetWithSettings d = ins.readMetadata().get();
    
    assertEquals(DatasetType.TAXONOMIC, d.getType());
    assertEquals(DataFormat.ACEF, d.getDataFormat());
    assertEquals("ILDIS World", d.getTitle());
    assertEquals("ILDIS", d.getAlias());
    assertEquals("12, May 2014", d.getVersion());
    assertNotNull(d.getReleased());
    //assertEquals("2014-05-05", d.getReleased().toString());
    assertEquals(1, d.getAuthors().size());
    assertEquals(0, d.getEditors().size());
    assertThat(d.getAuthors(), is(Lists.newArrayList("Roskov Y.R.")));
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


}