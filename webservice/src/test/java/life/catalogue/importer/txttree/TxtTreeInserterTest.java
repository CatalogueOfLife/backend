package life.catalogue.importer.txttree;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.TxtTreeTerm;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.neo.model.NeoUsage;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class TxtTreeInserterTest extends InserterBaseTest {

  @Override
  public NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException {
    return new TxtTreeInserter(store, resource);
  }

  @Test
  public void test2() throws Exception {
    NeoInserter ins = setup("/text-tree/2");
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
    }
  }
}