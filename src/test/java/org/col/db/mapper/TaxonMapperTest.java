package org.col.db.mapper;

import org.col.api.Taxon;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class TaxonMapperTest extends MapperTestBase<TaxonMapper> {

	public TaxonMapperTest() {
		super(TaxonMapper.class);
	}

	public Taxon create() throws Exception {
		Taxon t = new Taxon();
		t.setDataset(D1);
		t.setName(NAME1);
		t.setParent(TAXON1);
		return t;
	}

	@Test
	public void roundtrip() throws Exception {
		Taxon in = create();
		in.setId("t1");
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		
		Taxon out = mapper().get(D1.getKey(), in.getId());
		assertTrue(in.equalsShallow(out));
	}
}