package life.catalogue.importer.txttree;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.Language;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.neo.model.NeoUsage;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TxtTreeInserterTest extends InserterBaseTest {

  @Override
  public NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException {
    refFactory = new ReferenceFactory(d.getKey(), store.references(), null);
    return new TxtTreeInserter(store, resource, refFactory);
  }

  @Test
  public void test2() throws Exception {
    NeoInserter ins = setup("/txtree/2");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = store.usageWithName("13");
      assertNotNull(u.getVerbatimKey());
      VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
      assertEquals("6", v.get(TxtTreeTerm.indent));
      assertEquals("Acer negundo var. californicum (Torr. & Gray) Sarg. [variety]", v.get(TxtTreeTerm.content));
      assertEquals("Acer negundo var. californicum", u.usage.getName().getScientificName());
      assertEquals("Acer", u.usage.getName().getGenus());
      assertEquals("negundo", u.usage.getName().getSpecificEpithet());
      assertEquals("californicum", u.usage.getName().getInfraspecificEpithet());
      assertEquals(Rank.VARIETY, u.usage.getName().getRank());
      assertEquals("(Torr. & Gray) Sarg.", u.usage.getName().getAuthorship());
      assertEquals("Sarg.", u.usage.getName().getCombinationAuthorship().getAuthors().get(0));

      u = store.usageWithName("6");
      assertEquals("Acer negundo", u.usage.getName().getScientificName());
      assertEquals(3, u.vernacularNames.size());
      assertEquals("eng", u.vernacularNames.get(1).getLanguage());
      assertEquals("Box elder", u.vernacularNames.get(1).getName());
      assertEquals(Set.of(Environment.TERRESTRIAL), u.asTaxon().getEnvironments());

      u = store.usageWithName("20");
      assertEquals("Negundo aceroides subsp. violaceum", u.usage.getName().getScientificName());
      assertEquals("do we need to capture both?", u.usage.getRemarks());
    }
  }
}