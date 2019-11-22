package org.col.matching;

import java.util.concurrent.TimeUnit;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.Origin;
import org.col.db.PgSetupRule;
import org.col.db.mapper.MapperTestBase;
import org.col.db.mapper.NameMapper;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

public class NidxPgListenerTest extends MapperTestBase<NameMapper> {
  int counter = 0;
  NameIndex ni;
  
  public NidxPgListenerTest() {
    super(NameMapper.class);
  }
  
  @Test
  public void notification() throws Exception {
    NidxPgListener listener = new NidxPgListener(PgSetupRule.getCfg(), ni);
    NameMapper nm = mapper();
    nm.create(idxName(Rank.SPECIES, "Abies alba", "Mill."));
    nm.create(idxName(Rank.SPECIES, "Abies alba", "Mill."));
    commit();
    TimeUnit.SECONDS.sleep(10);
  }
  
  private Name idxName(Rank rank, String name, String auth) {
    Name n = new Name();
    n.setDatasetKey(Datasets.NAME_INDEX);
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);
    n.setId("nidx" + counter++);
    n.setRank(rank);
    n.setScientificName(name);
    n.setAuthorship(auth);
    TestEntityGenerator.setUser(n);
    return n;
  }
}