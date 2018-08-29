package org.col.db.mapper;

import java.util.List;

import org.col.api.model.VernacularName;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
public class VernacularNameMapperTest extends MapperTestBase<VernacularNameMapper> {

	public VernacularNameMapperTest() {
		super(VernacularNameMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		final int datasetKey = 2;

		VernacularName in = newVernacularName("cat");
		mapper().create(in, 1, datasetKey);
		assertNotNull(in.getKey());
		commit();
		VernacularName out = mapper().get(datasetKey, in.getKey());
		assertTrue(in.equals(out));
	}

	@Test
	public void testListByTaxon() throws Exception {
		VernacularName b = newVernacularName("b");
		mapper().create(b, TAXON2.getKey(), DATASET1.getKey());
		VernacularName c = newVernacularName("c");
		mapper().create(c, TAXON2.getKey(), DATASET1.getKey());
		VernacularName a = newVernacularName("a");
		mapper().create(a, TAXON2.getKey(), DATASET1.getKey());

		List<VernacularName> list = mapper().listByTaxon(TAXON2.getDatasetKey(), TAXON2.getKey());
		assertEquals(3, list.size());
		assertTrue(a.equals(list.get(0)));
		assertTrue(b.equals(list.get(1)));
		assertTrue(c.equals(list.get(2)));
	}

}