package org.col.dw.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.dw.TestEntityGenerator;
import org.col.dw.api.Name;
import org.col.dw.api.Synonymy;
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
			syn21.setBasionymKey(syn2bas.getKey());
			dao.create(syn21);

			Name syn22 = TestEntityGenerator.newName("syn2.2");
			syn22.setBasionymKey(syn2bas.getKey());
			dao.create(syn22);

			// homotypic 3
			Name syn3bas = TestEntityGenerator.newName("syn3bas");
			dao.create(syn3bas);

			Name syn31 = TestEntityGenerator.newName("syn3.1");
			syn31.setBasionymKey(syn3bas.getKey());
			dao.create(syn31);

			session.commit();

			// no synonym links added yet, expect empty synonymy even though basionym links
			// exist!
			synonymy = dao.getSynonymy(accKey);
			assertTrue(synonymy.isEmpty());
			assertEquals(0, synonymy.size());

			// now add a few synonyms
			dao.addSynonym(datasetKey, accKey, syn1.getKey());
			session.commit();

			synonymy = dao.getSynonymy(accKey);
			assertFalse(synonymy.isEmpty());
			assertEquals(1, synonymy.size());
			assertEquals(1, synonymy.listHomotypicGroups().size());

			dao.addSynonym(datasetKey, accKey, syn2bas.getKey());
			dao.addSynonym(datasetKey, accKey, syn21.getKey());
			dao.addSynonym(datasetKey, accKey, syn22.getKey());
			dao.addSynonym(datasetKey, accKey, syn3bas.getKey());
			dao.addSynonym(datasetKey, accKey, syn31.getKey());
			session.commit();

			synonymy = dao.getSynonymy(accKey);
			assertEquals(6, synonymy.size());
			assertEquals(3, synonymy.listHomotypicGroups().size());
		}
	}
}
