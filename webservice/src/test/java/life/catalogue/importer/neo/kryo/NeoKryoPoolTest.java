package life.catalogue.importer.neo.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.importer.neo.NeoKryoPool;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;
import org.gbif.dwc.terms.*;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NeoKryoPoolTest {
  Kryo kryo = new NeoKryoPool(1).create();

  @Test
  public void testNeoTaxon() throws Exception {
    NeoUsage t = new NeoUsage();
  
    Taxon taxon = new Taxon();
    taxon.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    taxon.setName(new Name());
    taxon.getName().setScientificName("Abies alba");
    taxon.getName().setCombinationAuthorship(TestEntityGenerator.createAuthorship());
    taxon.getName().setBasionymAuthorship(TestEntityGenerator.createAuthorship());
    taxon.getName().setRank(Rank.SPECIES);
    t.usage = taxon;
    assertSerde(t);
  }
  
  @Test
  public void testNeoName() throws Exception {
    NeoName nn = new NeoName(new Name());
    nn.getName().setScientificName("Abies alba");
    nn.getName().setCombinationAuthorship(TestEntityGenerator.createAuthorship());
    nn.getName().setBasionymAuthorship(TestEntityGenerator.createAuthorship());
    nn.getName().setRank(Rank.SPECIES);
    assertSerde(nn);
  }

  @Test
  public void testReference() throws Exception {
    Reference r = new Reference();
    r.setId("1234");
    r.setYear(1984);
    r.setDatasetKey(77);
    r.setCsl(TestEntityGenerator.createCsl());
    assertSerde(r);
  }
  
  @Test
  public void testVerbatim() throws Exception {
    List<Term> terms = Lists.newArrayList(
        DwcTerm.scientificName, DwcTerm.associatedOrganisms, DwcTerm.taxonID,
        DcTerm.title,
        GbifTerm.canonicalName,
        IucnTerm.threatStatus,
        AcefTerm.Family,
        UnknownTerm.build("http://gbif.org/abcdefg")
    );
    assertSerde(terms);
    
    VerbatimRecord rec = TestEntityGenerator.createVerbatim();
    for (Issue issue : Issue.values()) {
      rec.addIssue(issue);
    }
    assertSerde(rec);
  }
  
  @Test
  public void testEmptyModels() throws Exception {
    assertSerde(new NeoUsage());
    assertSerde(new Reference());
    assertSerde(new DatasetImport());
  }
  
  private void assertSerde(Object obj) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
    Output output = new Output(buffer);
    kryo.writeObject(output, obj);
    output.close();
    byte[] bytes = buffer.toByteArray();
    
    final Input input = new Input(bytes);
    Object obj2 = kryo.readObject(input, obj.getClass());
    
    assertEquals(obj, obj2);
  }
  
}