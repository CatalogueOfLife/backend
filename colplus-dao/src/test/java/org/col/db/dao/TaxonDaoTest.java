package org.col.db.dao;

import com.google.common.collect.Sets;
import org.col.api.model.Distribution;
import org.col.api.model.TaxonInfo;
import org.col.api.vocab.Gazetteer;
import org.col.util.BeanPrinter;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class TaxonDaoTest extends DaoTestBase {

	@Test
	public void testInfo() throws Exception {
		TaxonDao dao = new TaxonDao(session());
		TaxonInfo info = dao.getTaxonInfo(1);
		BeanPrinter.out(info);

		// See apple.sql
		assertEquals("01", "root-1", info.getTaxon().getId());
		assertEquals("02", 1, info.getTaxonReferences().size());
		assertEquals("04", 3, info.getVernacularNames().size());
		assertEquals("05", 2, info.getReferences().size());

		Set<Integer> refKeys1 = new HashSet<>();
		info.getReferences().values().forEach(e -> refKeys1.add(e.getKey()));

		Set<Integer> refKeys2 = new HashSet<>();
		refKeys2.addAll(info.getTaxonReferences());
    refKeys2.addAll(info.getTaxonReferences());
    refKeys2.addAll(info.getTaxonReferences());
		info.getDistributions().forEach(d -> refKeys2.addAll(d.getReferenceKeys()));
    info.getVernacularNames().forEach(d -> refKeys2.addAll(d.getReferenceKeys()));

		assertEquals("06", refKeys1, refKeys2);

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

}
