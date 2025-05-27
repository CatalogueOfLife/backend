package life.catalogue.common.kryo;

import com.esotericsoftware.kryo.util.Pool;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.*;
import life.catalogue.common.date.FuzzyDate;

import org.gbif.dwc.terms.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ApiKryoPoolTest {
  Pool<Kryo> kryo = new ApiKryoPool(1);
  
  @Test
  public void testName() throws Exception {
    Name n = TestEntityGenerator.newName("1234567");
    assertSerde(n);
  }

  @Test
  public void testSector() throws Exception {
    Sector s = new Sector();
    s.setId(1234);
    s.setDatasetKey(77);
    s.setSubject(new SimpleNameLink());
    s.setSubjectDatasetKey(123);
    s.setTarget(new SimpleNameLink());
    s.setMode(Sector.Mode.ATTACH);
    s.setRanks(Set.of(Rank.SPECIES, Rank.GENUS));
    s.setEntities(Set.of(EntityType.NAME, EntityType.TAXON));
    s.setExtinctFilter(true);
    s.setNameTypes(Set.of(NameType.SCIENTIFIC, NameType.VIRUS));
    s.setPriority(12);
    s.applyUser(TestEntityGenerator.USER_EDITOR);
    s.applyCreatedNow();
    assertSerde(s);
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
  public void testTypeMaterial() throws Exception {
    var d = new TypeMaterial();
    d.setId("1234");
    d.setReferenceId("1984");
    d.setStatus(TypeStatus.ALLOLECTOTYPE);
    d.setSectorKey(13);
    d.setVerbatimKey(6789);
    d.setSex(Sex.FEMALE);
    d.setAssociatedSequences("my sequence");
    d.setCoordinate(new Coordinate(-1.7891,2.12));
    d.setAltitude("1000m");
    d.setCatalogNumber("45612");
    d.setInstitutionCode("B");
    d.setCitation("cite me like this");
    d.setCountry(Country.AFGHANISTAN);
    d.setDate("my date");
    d.setHost("my host");
    d.setCollector("my collector");
    d.setNameId("nameID");
    d.setLink(URI.create("http://gbif.org/234567"));
    d.setLatitude("12°31``2`");
    d.setLongitude("21°32``12`");
    assertSerde(d);
  }

  @Test
  public void testAreas() throws Exception {
    var d = new Distribution();
    d.setId(1234);
    d.setReferenceId("1984");
    d.setStatus(DistributionStatus.NATIVE);
    d.setSectorKey(13);
    d.setVerbatimKey(6789);
    d.setArea(new AreaImpl("Berlin"));
    assertSerde(d);

    d.setArea(Country.ALBANIA);
    assertSerde(d);

    d.setArea(TdwgArea.of("BZN"));
    assertSerde(d);
  }

  @Test
  public void testDataset() throws Exception {
    Dataset d = TestEntityGenerator.newFullDataset(1234);
    assertSerde(d);
  }

  @Test
  public void testUsages() throws Exception {
    Taxon t = TestEntityGenerator.newTaxon("bla bla");
    assertSerde(t);

    Synonym s = TestEntityGenerator.newSynonym(t);
    assertSerde(s);
  }

  @Test
  public void testUsageWrappers() throws Exception {
    NameUsageWrapper nuw = TestEntityGenerator.newNameUsageSynonymWrapper();
    assertSerde(nuw);

    nuw = TestEntityGenerator.newNameUsageTaxonWrapper();
    assertSerde(nuw);

    nuw = TestEntityGenerator.newNameUsageBareNameWrapper();
    assertSerde(nuw);
  }

  @Test
  public void testVerbatim() throws Exception {
    List<Term> terms = Lists.newArrayList(
        DwcTerm.scientificName, DwcTerm.associatedOrganisms, DwcTerm.taxonID,
        DcTerm.title,
        GbifTerm.canonicalName,
        IucnTerm.threatStatus,
        AcefTerm.Family,
        WfoTerm.ipniID,
        TxtTreeTerm.content,
        BiboOntTerm.journal,
        EolDocumentTerm.Document,
        InatTerm.lexicon,
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
    assertSerde(new Taxon());
    assertSerde(new Name());
    assertSerde(new Reference());
    assertSerde(new Dataset());
    assertSerde(new DatasetImport());
  }


  @Test
  public void testFastutilList() throws Exception {
    Name n = TestEntityGenerator.newName("1234567");
    var authors = n.getCombinationAuthorship().getAuthors();
    n.getCombinationAuthorship().setAuthors(new ObjectArrayList<>(authors));
    assertSerde(n);
  }

  public void assertSerde(Object obj) {
    assertSerde(kryo, obj);
  }

  public static void assertSerde(Pool<Kryo> pool, Object obj) {
    Kryo kryo = pool.obtain();
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
      Output output = new Output(buffer);
      kryo.writeObject(output, obj);
      output.close();
      byte[] bytes = buffer.toByteArray();

      final Input input = new Input(bytes);
      Object obj2 = kryo.readObject(input, obj.getClass());

      assertEquals(obj, obj2);
    } finally {
      pool.free(kryo);
    }
  }

}