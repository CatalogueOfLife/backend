package org.col.db.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.model.Synonymy;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameDaoTest extends DaoTestBase {

  @Test
  public void synonyms() throws Exception {
    try (SqlSession session = session()) {
      NameDao dao = new NameDao(session());

      final int accKey = TestEntityGenerator.TAXON1.getKey();
      final int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();

      Synonymy synonymy = dao.getSynonymy(accKey);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());

      // homotypic 1
      Name syn1 = TestEntityGenerator.newName("syn1");
      dao.create(syn1);

      // homotypic 2
      Name syn2bas = TestEntityGenerator.newName("syn2bas");
      dao.create(syn2bas);

      Name syn21 = TestEntityGenerator.newName("syn2.1");
      syn21.setHomotypicNameKey(syn2bas.getKey());
      dao.create(syn21);

      Name syn22 = TestEntityGenerator.newName("syn2.2");
      syn22.setHomotypicNameKey(syn2bas.getKey());
      dao.create(syn22);

      // homotypic 3
      Name syn3bas = TestEntityGenerator.newName("syn3bas");
      dao.create(syn3bas);

      Name syn31 = TestEntityGenerator.newName("syn3.1");
      syn31.setHomotypicNameKey(syn3bas.getKey());
      dao.create(syn31);

      session.commit();

      // no synonym links added yet, expect empty synonymy even though basionym links
      // exist!
      synonymy = dao.getSynonymy(accKey);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());

      // now add a few synonyms
      dao.addSynonym(TestEntityGenerator.newMisapplied(syn1, accKey));
      session.commit();

      synonymy = dao.getSynonymy(accKey);
      assertFalse(synonymy.isEmpty());
      assertEquals(1, synonymy.size());
      assertEquals(1, synonymy.getMisapplied().size());

      dao.addSynonym(TestEntityGenerator.newMisapplied(syn2bas, accKey));
      dao.addSynonym(TestEntityGenerator.newMisapplied(syn21, accKey));
      dao.addSynonym(TestEntityGenerator.newMisapplied(syn22, accKey));
      dao.addSynonym(TestEntityGenerator.newMisapplied(syn3bas, accKey));
      dao.addSynonym(TestEntityGenerator.newMisapplied(syn31, accKey));
      session.commit();

      synonymy = dao.getSynonymy(accKey);
      assertEquals(6, synonymy.size());
      assertEquals(3, synonymy.getMisapplied().size());
    }
  }
  
}
