package org.col.admin.importer.neo.kryo;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import org.col.admin.importer.neo.model.NeoTaxon;
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
    NeoTaxon t = new NeoTaxon();

    t.taxon = new Taxon();
    t.taxon.setDoubtful(true);

    t.taxon.setName(new Name());
    t.taxon.getName().setScientificName("Abies alba");
    t.taxon.getName().setCombinationAuthorship(TestEntityGenerator.createAuthorship());
    t.taxon.getName().setBasionymAuthorship(TestEntityGenerator.createAuthorship());
    t.taxon.getName().setRank(Rank.SPECIES);
    assertSerde(t);
  }

  @Test
  public void testReference() throws Exception {
    Reference r = new Reference();
    r.setId("1234");
    r.setKey(123);
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

    TermRecord rec = TestEntityGenerator.createVerbatim();
    for (Issue issue : Issue.values()) {
      rec.addIssue(issue);
    }
    assertSerde(rec);
  }

  @Test
  public void testEmptyModels() throws Exception {
    assertSerde(new NeoTaxon());
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