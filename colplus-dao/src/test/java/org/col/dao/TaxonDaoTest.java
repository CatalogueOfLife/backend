package org.col.dao;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.ibatis.session.SqlSession;
import org.col.api.BeanPrinter;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

public class TaxonDaoTest extends DaoTestBase {
  
  @Test
  public void testInfo() throws Exception {
    final int datasetKey = DATASET11.getKey();
    TaxonDao dao = new TaxonDao(session);
    TaxonInfo info = dao.getTaxonInfo(datasetKey, TAXON1.getId());
    BeanPrinter.out(info);
    
    // See apple.sql
    assertEquals("root-1", info.getTaxon().getId());
    assertEquals(1, info.getTaxonReferences().size());
    assertEquals(3, info.getVernacularNames().size());
    assertEquals(2, info.getReferences().size());
    
    Set<String> refKeys1 = new HashSet<>();
    info.getReferences().values().forEach(r -> refKeys1.add(r.getId()));
    
    Set<String> refKeys2 = new HashSet<>();
    refKeys2.addAll(info.getTaxonReferences());
    
    Stream.concat(
        info.getDescriptions().stream(),
        Stream.concat(
            info.getDistributions().stream(),
            Stream.concat(
                info.getMedia().stream(),
                info.getVernacularNames().stream()
            )
        )
    ).filter(r -> r.getReferenceId() != null)
     .forEach(r -> refKeys2.add(r.getReferenceId()));

		assertEquals(refKeys1, refKeys2);

    assertEquals(2, info.getDistributions().size());
    for (Distribution d : info.getDistributions()) {
      switch (d.getKey()) {
        case 1:
          assertEquals("Berlin", d.getArea());
          assertEquals(Gazetteer.TEXT, d.getGazetteer());
          assertNull(d.getStatus());
          assertEquals("ref-1", d.getReferenceId());
          break;
        case 2:
          assertEquals("Leiden", d.getArea());
          assertEquals(Gazetteer.TEXT, d.getGazetteer());
          assertNull(d.getStatus());
          assertEquals("ref-1b" ,d.getReferenceId());
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
      syn21.setHomotypicNameId(syn2bas.getId());
      nDao.create(syn21);
      
      Name syn22 = TestEntityGenerator.newName("syn2.2");
      syn22.setHomotypicNameId(syn2bas.getId());
      nDao.create(syn22);
      
      // homotypic 3
      Name syn3bas = TestEntityGenerator.newName("syn3bas");
      nDao.create(syn3bas);
      
      Name syn31 = TestEntityGenerator.newName("syn3.1");
      syn31.setHomotypicNameId(syn3bas.getId());
      nDao.create(syn31);
      
      session.commit();
      
      // no synonym links added yet, expect empty synonymy as no homotypic synnym exists
      synonymy = tDao.getSynonymy(acc);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());
      
      // now add a single synonym relation
      Synonym syn = setUserDate(new Synonym());
      syn.setStatus(TaxonomicStatus.SYNONYM);
      syn.setOrigin(Origin.SOURCE);
      nDao.addSynonym(datasetKey, syn1.getId(), acc.getId(), syn);
      session.commit();
      
      synonymy = tDao.getSynonymy(acc);
      assertFalse(synonymy.isEmpty());
      assertEquals(1, synonymy.size());
      assertEquals(0, synonymy.getMisapplied().size());
      assertEquals(0, synonymy.getHomotypic().size());
      
      nDao.addSynonym(datasetKey, syn2bas.getId(), acc.getId(), syn);
      nDao.addSynonym(datasetKey, syn3bas.getId(), acc.getId(), syn);
      syn.setStatus(TaxonomicStatus.MISAPPLIED);
      nDao.addSynonym(datasetKey, syn21.getId(), acc.getId(), syn);
      session.commit();
      
      // at this stage we have 4 explicit synonym relations
      synonymy = tDao.getSynonymy(acc);
      assertEquals(4, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(1, synonymy.getMisapplied().size());
      
      // add the remaining homotypic names as synonyms
      syn.setStatus(TaxonomicStatus.SYNONYM);
      nDao.addSynonym(datasetKey, syn21.getId(), acc.getId(), syn);
      nDao.addSynonym(datasetKey, syn22.getId(), acc.getId(), syn);
      nDao.addSynonym(datasetKey, syn31.getId(), acc.getId(), syn);
      
      synonymy = tDao.getSynonymy(acc);
      assertEquals(7, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      // still the same number of heterotypic synonym groups
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(1, synonymy.getMisapplied().size());
      
      synonymy = tDao.getSynonymy(TAXON2);
      
    }
  }
  
}
