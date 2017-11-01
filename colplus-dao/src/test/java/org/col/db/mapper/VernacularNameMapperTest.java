package org.col.db.mapper;

import static org.col.dao.DaoTestUtil.DATASET1;
import static org.col.dao.DaoTestUtil.TAXON1;
import static org.col.dao.DaoTestUtil.newVernacularName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.col.api.VernacularName;
import org.junit.Test;

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
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		VernacularName out = mapper().getByKey(in.getKey());
		assertTrue(in.equals(out));
	}

	@Test
	public void testGetVernacularNames() throws Exception {
		VernacularName b = newVernacularName("b");
		mapper().create(b);
		VernacularName c = newVernacularName("c");
		mapper().create(c);
		VernacularName a = newVernacularName("a");
		mapper().create(a);
		List<VernacularName> list = mapper().getVernacularNames(DATASET1.getKey(), TAXON1.getId());
		assertEquals(3, list.size());
		assertTrue(a.equals(list.get(0)));
		assertTrue(b.equals(list.get(1)));
		assertTrue(c.equals(list.get(2)));
	}

}