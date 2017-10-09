package org.col.db.mapper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
		VernacularName out = mapper().get(D1.getKey(), in.getName());
		assertTrue(in.equalsShallow(out));
	}

	// @Test
	// public void count() throws Exception {
	// int i = mapper().count(D1.getKey());
	// // Just to make sure we understand our environment
	// // 2 Taxa pre-inserted through InitMybatisRule.squirrels()
	// assertEquals(2, i);
	// mapper().create(create("t2"));
	// mapper().create(create("t3"));
	// mapper().create(create("t4"));
	// assertEquals(5, mapper().count(D1.getKey()));
	// }
	//
	// @Test
	// public void list() throws Exception {
	// List<VernacularName> taxa = new ArrayList<>();
	// taxa.add(create("t1"));
	// taxa.add(create("t2"));
	// taxa.add(create("t3"));
	// taxa.add(create("t4"));
	// taxa.add(create("t5"));
	// taxa.add(create("t6"));
	// taxa.add(create("t7"));
	// taxa.add(create("t8"));
	// taxa.add(create("t9"));
	// for(VernacularName t : taxa) {
	// mapper().create(t);
	// }
	// commit();
	//
	// // get first page
	// Page p = new Page(0,3);
	//
	// List<VernacularName> res = mapper().list(D1.getKey(), p);
	// assertEquals(3, res.size());
	// // First 2 taxa in dataset D1 are pre-inserted taxa:
	// assertTrue(TAXON1.equalsShallow(res.get(0)));
	// assertTrue(TAXON2.equalsShallow(res.get(1)));
	// assertTrue(taxa.get(0).equalsShallow(res.get(2)));
	//
	// p.next();
	// res = mapper().list(D1.getKey(), p);
	// assertEquals(3, res.size());
	// assertTrue(taxa.get(1).equalsShallow(res.get(0)));
	// assertTrue(taxa.get(2).equalsShallow(res.get(1)));
	// assertTrue(taxa.get(3).equalsShallow(res.get(2)));
	//
	// }

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