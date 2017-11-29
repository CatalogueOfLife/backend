package org.col.db.mapper;

import org.col.api.VernacularName;
import org.junit.Test;

import java.util.List;

import static org.col.TestEntityGenerator.*;
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
		VernacularName in = newVernacularName("cat");
		mapper().create(in, 1, 1);
		assertNotNull(in.getKey());
		commit();
		VernacularName out = mapper().get(in.getKey());
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

		List<VernacularName> list = mapper().listByTaxon(TAXON2.getKey());
		assertEquals(3, list.size());
		assertTrue(a.equals(list.get(0)));
		assertTrue(b.equals(list.get(1)));
		assertTrue(c.equals(list.get(2)));
	}

}