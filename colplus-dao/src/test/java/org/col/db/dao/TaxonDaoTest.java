package org.col.db.dao;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.col.api.BeanPrinter;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;

public class TaxonDaoTest extends DaoTestBase {

	@Test
	public void testInfo() throws Exception {
	  final int datasetKey = DATASET11.getKey();
		TaxonDao dao = new TaxonDao(session);
		TaxonInfo info = dao.getTaxonInfo(datasetKey, 1);
		BeanPrinter.out(info);

		// See apple.sql
		assertEquals("root-1", info.getTaxon().getId());
		assertEquals(1, info.getTaxonReferences().size());
		assertEquals(3, info.getVernacularNames().size());
		assertEquals(2, info.getReferences().size());

		Set<Integer> refKeys1 = new HashSet<>();
		info.getReferences().values().forEach(r -> refKeys1.add(r.getKey()));

		Set<Integer> refKeys2 = new HashSet<>();
		refKeys2.addAll(info.getTaxonReferences());
    refKeys2.addAll(info.getTaxonReferences());
    refKeys2.addAll(info.getTaxonReferences());
		info.getDistributions().forEach(d -> refKeys2.addAll(d.getReferenceKeys()));
    info.getVernacularNames().forEach(d -> refKeys2.addAll(d.getReferenceKeys()));

		assertEquals(refKeys1, refKeys2);

    assertEquals(2, info.getDistributions().size());
		for (Distribution d : info.getDistributions()) {
		  switch (d.getKey()) {
        case 1:
		      assertEquals("Berlin", d.getArea());
          assertEquals(Gazetteer.TEXT, d.getGazetteer());
          assertNull(d.getStatus());
          assertEquals(d.getReferenceKeys(), Sets.newHashSet(1, 2));
          break;
        case 2:
          assertEquals("Leiden", d.getArea());
          assertEquals(Gazetteer.TEXT, d.getGazetteer());
          assertNull(d.getStatus());
          assertEquals(d.getReferenceKeys(), Sets.newHashSet(2));
          break;
        default:
          fail("Unexpected distribution");
      }
    }
	}

  @Test
  public void synonyms() throws Exception {
    try (SqlSession session = session()) {
      TaxonDao tDao = new TaxonDao(session());
      NameDao nDao = new NameDao(session());

      final Taxon acc = TestEntityGenerator.TAXON1;
      final int datasetKey = acc.getDatasetKey();

      Synonymy synonymy = tDao.getSynonymy(acc);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());

      // homotypic 1
      Name syn1 = TestEntityGenerator.newName("syn1");
      nDao.create(syn1);

      // homotypic 2
      Name syn2bas = TestEntityGenerator.newName("syn2bas");
      nDao.create(syn2bas);

      Name syn21 = TestEntityGenerator.newName("syn2.1");
      syn21.setHomotypicNameKey(syn2bas.getKey());
      nDao.create(syn21);

      Name syn22 = TestEntityGenerator.newName("syn2.2");
      syn22.setHomotypicNameKey(syn2bas.getKey());
      nDao.create(syn22);

      // homotypic 3
      Name syn3bas = TestEntityGenerator.newName("syn3bas");
      nDao.create(syn3bas);

      Name syn31 = TestEntityGenerator.newName("syn3.1");
      syn31.setHomotypicNameKey(syn3bas.getKey());
      nDao.create(syn31);

      session.commit();

      // no synonym links added yet, expect empty synonymy even though basionym links
      // exist!
      synonymy = tDao.getSynonymy(acc);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());

      // now add a single synonym relation
      Synonym syn = new Synonym();
      syn.setStatus(TaxonomicStatus.SYNONYM);
      nDao.addSynonym(datasetKey, syn1.getKey(), acc.getKey(), syn);
      session.commit();

      synonymy = tDao.getSynonymy(acc);
      assertFalse(synonymy.isEmpty());
      assertEquals(1, synonymy.size());
      assertEquals(0, synonymy.getMisapplied().size());
      assertEquals(0, synonymy.getHomotypic().size());

      nDao.addSynonym(datasetKey, syn2bas.getKey(), acc.getKey(), syn);
      nDao.addSynonym(datasetKey, syn3bas.getKey(), acc.getKey(), syn);
      syn.setStatus(TaxonomicStatus.MISAPPLIED);
      nDao.addSynonym(datasetKey, syn21.getKey(), acc.getKey(), syn);
      session.commit();

      // at this stage we have 4 explicit synonym relations
      synonymy = tDao.getSynonymy(acc);
      assertEquals(4, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(1, synonymy.getMisapplied().size());

      // add the remaining homotypic names as synonyms
      syn.setStatus(TaxonomicStatus.SYNONYM);
      nDao.addSynonym(datasetKey, syn21.getKey(), acc.getKey(), syn);
      nDao.addSynonym(datasetKey, syn22.getKey(), acc.getKey(), syn);
      nDao.addSynonym(datasetKey, syn31.getKey(), acc.getKey(), syn);

      synonymy = tDao.getSynonymy(acc);
      assertEquals(7, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      // still the same number of heterotypic synonym groups
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(1, synonymy.getMisapplied().size());
    }
  }

}
