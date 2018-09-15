package org.col.db.dao;

import java.util.List;

import org.col.api.model.NameUsage;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME1;
import static org.col.api.TestEntityGenerator.NAME3;
import static org.junit.Assert.assertEquals;

public class NameUsageDaoTest extends DaoTestBase {

  NameUsageDao dao;

  @Before
  public void init(){
    dao = new NameUsageDao(session);
  }

  @Test
  public void byName() throws Exception {
    List<NameUsage> usages = dao.byNameId(NAME1.getDatasetKey(), NAME1.getId());
    assertEquals(1, usages.size());
    assertEquals(Taxon.class, usages.get(0).getClass());
    assertEquals(NAME1.getId(), (usages.get(0)).getName().getId());

    usages = dao.byName(NAME3);
    assertEquals(1, usages.size());
    assertEquals(Synonym.class, usages.get(0).getClass());
    assertEquals("root-2", ((Synonym)usages.get(0)).getAccepted().getId());
  }

}