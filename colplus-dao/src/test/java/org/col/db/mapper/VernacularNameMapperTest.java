package org.col.db.mapper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.*;

import java.util.List;

import org.col.api.VernacularName;
import org.col.api.vocab.Country;
import org.col.api.vocab.Language;
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
		VernacularName in = create("cat");
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		VernacularName out = mapper().getByInternalKey(in.getKey());
		assertTrue(in.equalsShallow(out));
	}

	public void testGetVernacularNamesForTaxon() throws Exception {
		VernacularName b = create("b");
		mapper().create(b);
		VernacularName c = create("c");
		mapper().create(c);
		VernacularName a = create("a");
		mapper().create(a);
		List<VernacularName> list = mapper().getVernacularNamesForTaxon(D1.getKey(), TAXON1.getKey());
		assertEquals(3, list.size());
		assertTrue(a.equalsShallow(list.get(0)));
		assertTrue(b.equalsShallow(list.get(1)));
		assertTrue(c.equalsShallow(list.get(2)));
	}

	private static VernacularName create(String name) throws Exception {
		VernacularName vn = create();
		vn.setName(name);
		return vn;
	}

	private static VernacularName create() throws Exception {
		VernacularName t = new VernacularName();
		t.setDataset(D1);
		t.setTaxon(TAXON1);
		t.setLanguage(Language.ENGLISH);
		t.setCountry(Country.UNITED_KINGDOM);
		return t;
	}

}