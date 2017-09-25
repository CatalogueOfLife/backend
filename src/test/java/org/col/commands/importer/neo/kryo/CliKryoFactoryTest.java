package org.col.commands.importer.neo.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import org.col.api.DatasetMetrics;
import org.col.api.Name;
import org.col.api.Reference;
import org.col.api.Taxon;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonIssue;
import org.col.api.vocab.TaxonomicStatus;
import org.col.commands.importer.neo.model.TaxonNameNode;
import org.gbif.dwc.terms.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CliKryoFactoryTest {
  Kryo kryo = new CliKryoFactory().create();

  @Test
  public void testTaxonNameNode() throws Exception {
    Taxon t = new Taxon();
    for (TaxonIssue issue : TaxonIssue.values()) {
      //t.addIssue(issue);
    }
    t.setStatus(TaxonomicStatus.DOUBTFUL);

    Name n = new Name();
    n.setScientificName("Abies alba");
    n.setAuthorship("Mill.");
    n.setRank(Rank.SPECIES);

    TaxonNameNode u = new TaxonNameNode(n, t);
    assertSerde(u);
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
    assertSerde(new TaxonNameNode());
    assertSerde(new Reference());
    assertSerde(new DatasetMetrics());
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