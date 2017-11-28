package org.col.dao;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.col.api.TaxonInfo;
import org.col.util.BeanPrinter;
import org.junit.Test;

public class TaxonDaoTest extends DaoTestBase {

	@Test
	public void testInfo() throws Exception {
		TaxonDao dao = new TaxonDao(session());
		TaxonInfo info = dao.getTaxonInfo(1);
		BeanPrinter.out(info);

		// See squirrels.sql
		assertEquals("01", "root-1", info.getTaxon().getId());
		assertEquals("02", 1, info.getTaxon().getReferences().size());
		assertEquals("03", 1, info.getTaxon().getReferences().size());
		assertEquals("04", 2, info.getDistributions().size());
		assertEquals("05", 2, info.getVernacularNames().size());
		assertEquals("06", 2, info.getReferences().size());

		Set<Integer> refKeys1 = new HashSet<>();
		info.getReferences().values().stream().forEach(e -> refKeys1.add(e.getKey()));

		Set<Integer> refKeys2 = new HashSet<>();
		info.getTaxon().getReferences().stream().forEach(e -> refKeys2.add(e.getReferenceKey()));
		info.getDistributions().stream()
		    .forEach(x -> x.getReferences().stream().forEach(e -> refKeys2.add(e.getReferenceKey())));
		info.getVernacularNames().stream()
		    .forEach(x -> x.getReferences().stream().forEach(e -> refKeys2.add(e.getReferenceKey())));

		assertEquals("07", refKeys1, refKeys2);

	}

}
