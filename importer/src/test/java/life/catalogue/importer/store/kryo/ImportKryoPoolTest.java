package life.catalogue.importer.store.kryo;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.importer.store.ImportKryoPool;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.RelationData;
import life.catalogue.importer.store.model.UsageData;

import org.gbif.dwc.terms.*;
import org.gbif.nameparser.api.Rank;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;

import org.junit.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;

import org.locationtech.jts.geom.Coordinates;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ImportKryoPoolTest {
  Kryo kryo = new ImportKryoPool(1).create();

  @Test
  public void testTaxonData() throws Exception {
    UsageData t = new UsageData();
    t.proParteAcceptedIDs.add("1234");
    t.proParteAcceptedIDs.add("edrftghj");
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
  public void testNameData() throws Exception {
    NameData nn = new NameData(new Name());
    nn.getName().setScientificName("Abies alba");
    nn.getName().setCombinationAuthorship(TestEntityGenerator.createAuthorship());
    nn.getName().setBasionymAuthorship(TestEntityGenerator.createAuthorship());
    nn.getName().setRank(Rank.SPECIES);
    nn.usageIDs.add("1234");
    nn.relations.add(new RelationData<>(NomRelType.REPLACEMENT_NAME, "fds", "6tz7u8ik"));
    assertSerde(nn);
  }

  @Test
  public void testMaterial() throws Exception {
    var m = new TypeMaterial();
    m.setId("1234");
    m.setDatasetKey(77);
    m.setCitation("swedrftgzhujk");
    m.setStatus(TypeStatus.HOLOTYPE);
    m.setNameId("12345678");
    m.setCollector("Collector");
    m.setRemarks("Remarks");
    m.setCountry(Country.GERMANY);
    m.setCoordinate(new Coordinate(12.34, 56.78));
    m.setSex(Sex.FEMALE);
    m.setLink(URI.create("http://www.gbif.org/"));
    assertSerde(m);
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
      rec.add(issue);
    }
    assertSerde(rec);
  }
  
  @Test
  public void testEmptyModels() throws Exception {
    assertSerde(new UsageData());
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