package org.col.db.mapper;

import static org.junit.Assert.assertNotNull;

import org.col.api.Name;
import org.col.api.Taxon;
import org.junit.Test;

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
		Taxon t1 = create();
		t1.setId("t1");
		mapper().insert(t1);
		assertNotNull(t1.getKey());
		commit();
	}
}