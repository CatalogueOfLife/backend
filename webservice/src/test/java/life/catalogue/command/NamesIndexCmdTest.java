package life.catalogue.command;

import life.catalogue.api.model.IndexName;
import life.catalogue.matching.nidx.NameIndexMapDBStore;

import life.catalogue.matching.nidx.NameIndexStore;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;
import org.mapdb.DBMaker;

/**
 *
 */
public class NamesIndexCmdTest {

  @Test
  public void kryo() throws Exception {
    NameIndexStore store = new NameIndexMapDBStore(DBMaker.memoryDB(),512);
    store.start();
    var idx = new IndexName();
    idx.setKey(778899);
    idx.setCanonicalId(778899);
    idx.setRank(Rank.SPECIES);
    idx.setScientificName("Abies alba");
    store.add("asdfgh", idx);
  }

}