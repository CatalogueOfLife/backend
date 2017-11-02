package org.col.db.mapper;

import org.col.TestEntityGenerator;
import org.col.api.Page;
import org.col.api.Taxon;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 *
 */
public class TaxonMapperTest extends MapperTestBase<TaxonMapper> {

	public TaxonMapperTest() {
		super(TaxonMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		Taxon in = TestEntityGenerator.newTaxon("t1");
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		Taxon out = mapper().get(mapper().lookupKey(TestEntityGenerator.DATASET1.getKey(), in.getId()));
		assertTrue(in.equalsShallow(out));
	}

	@Test
	public void count() throws Exception {
		int i = mapper().count(TestEntityGenerator.DATASET1.getKey());
		// Just to make sure we understand our environment
		// 2 Taxa pre-inserted through InitMybatisRule.squirrels()
		assertEquals(2, i);
		mapper().create(TestEntityGenerator.newTaxon("t2"));
		mapper().create(TestEntityGenerator.newTaxon("t3"));
		mapper().create(TestEntityGenerator.newTaxon("t4"));
		assertEquals(5, mapper().count(TestEntityGenerator.DATASET1.getKey()));
	}

	@Test
	public void list() throws Exception {
		List<Taxon> taxa = new ArrayList<>();
		taxa.add(TestEntityGenerator.newTaxon("t1"));
		taxa.add(TestEntityGenerator.newTaxon("t2"));
		taxa.add(TestEntityGenerator.newTaxon("t3"));
		taxa.add(TestEntityGenerator.newTaxon("t4"));
		taxa.add(TestEntityGenerator.newTaxon("t5"));
		taxa.add(TestEntityGenerator.newTaxon("t6"));
		taxa.add(TestEntityGenerator.newTaxon("t7"));
		taxa.add(TestEntityGenerator.newTaxon("t8"));
		taxa.add(TestEntityGenerator.newTaxon("t9"));
		for(Taxon t : taxa) {
			mapper().create(t);
		}
		commit();

    // get first page
    Page p = new Page(0,3);

    List<Taxon> res = mapper().list(TestEntityGenerator.DATASET1.getKey(), p);
    assertEquals(3, res.size());
    // First 2 taxa in dataset D1 are pre-inserted taxa:
    assertTrue(TestEntityGenerator.TAXON1.equalsShallow(res.get(0)));
    assertTrue(TestEntityGenerator.TAXON2.equalsShallow(res.get(1)));
    assertTrue(taxa.get(0).equalsShallow(res.get(2)));
    
		p.next();
		res = mapper().list(TestEntityGenerator.DATASET1.getKey(), p);
		assertEquals(3, res.size());
		assertTrue(taxa.get(1).equalsShallow(res.get(0)));
		assertTrue(taxa.get(2).equalsShallow(res.get(1)));
		assertTrue(taxa.get(3).equalsShallow(res.get(2)));

	}
}