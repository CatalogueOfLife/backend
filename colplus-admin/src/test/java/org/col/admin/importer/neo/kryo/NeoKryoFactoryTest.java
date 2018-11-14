package org.col.admin.importer.neo.kryo;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import org.col.admin.importer.neo.NeoKryoFactory;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.*;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NeoKryoFactoryTest {
  Kryo kryo = new NeoKryoFactory().create();

  @Test
  public void testNeoTaxon() throws Exception {
    NeoUsage t = new NeoUsage();
  
    Taxon taxon = new Taxon();
    taxon.setDoubtful(true);
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
    NeoName nn = new NeoName();
    
    nn.name = new Name();
    nn.name.setScientificName("Abies alba");
    nn.name.setCombinationAuthorship(TestEntityGenerator.createAuthorship());
    nn.name.setBasionymAuthorship(TestEntityGenerator.createAuthorship());
    nn.name.setRank(Rank.SPECIES);
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