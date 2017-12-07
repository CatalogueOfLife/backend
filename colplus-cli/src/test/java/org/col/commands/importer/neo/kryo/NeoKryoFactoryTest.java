package org.col.commands.importer.neo.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import org.col.TestEntityGenerator;
import org.col.api.*;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.api.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.gbif.dwc.terms.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;

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
    t.taxon.setStatus(TaxonomicStatus.DOUBTFUL);

    t.taxon.setName(new Name());
    t.taxon.getName().setScientificName("Abies alba");
    t.taxon.getName().setAuthorship(TestEntityGenerator.createAuthorship());
    t.taxon.getName().setBasionymAuthorship(TestEntityGenerator.createAuthorship());
    t.taxon.getName().setRank(Rank.SPECIES);
    for (Issue issue : Issue.values()) {
      t.taxon.addIssue(issue);
    }

    t.verbatim = new VerbatimRecord();
    for (Term term : GbifTerm.values()) {
      t.verbatim.setCoreTerm(term, term.simpleName());
    }

    assertSerde(t);
  }

  @Test
  public void testTerms() throws Exception {
    List<Term> terms = Lists.newArrayList(
        DwcTerm.scientificName, DwcTerm.associatedOrganisms, DwcTerm.taxonID,
        DcTerm.title,
        GbifTerm.canonicalName,
        IucnTerm.threatStatus, EolReferenceTerm.primaryTitle, new UnknownTerm(URI.create("http://gbif.org/abcdefg"))
    );
    assertSerde(terms);
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