package org.col.db.mapper;

import org.col.TestEntityGenerator;
import org.col.api.Page;
import org.col.api.Taxon;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
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
		Taxon out = mapper().get(in.getKey());

		Javers javers = JaversBuilder.javers().build();
		Diff diff = javers.compare(in, out);
		assertEquals(0, diff.getChanges().size());
		assertEquals(in, out);
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
		for (Taxon t : taxa) {
			mapper().create(t);
		}
		commit();

		// get first page
		Page p = new Page(0, 3);

		List<Taxon> res = mapper().list(TestEntityGenerator.DATASET1.getKey(), p);
		assertEquals(3, res.size());
		// First 2 taxa in dataset D1 are pre-inserted taxa:
		assertTrue(TestEntityGenerator.TAXON1.getKey().equals(res.get(0).getKey()));
		assertTrue(TestEntityGenerator.TAXON2.getKey().equals(res.get(1).getKey()));
		;
		assertTrue(taxa.get(0).getKey().equals(res.get(2).getKey()));

		p.next();
		res = mapper().list(TestEntityGenerator.DATASET1.getKey(), p);
		assertEquals(3, res.size());
		assertTrue(taxa.get(1).getKey().equals(res.get(0).getKey()));
		assertTrue(taxa.get(2).getKey().equals(res.get(1).getKey()));
		assertTrue(taxa.get(3).getKey().equals(res.get(2).getKey()));

	}

	@Test
	public void children() throws Exception {
		Taxon parent = TestEntityGenerator.newTaxon("parent-1");
		mapper().create(parent);

		Taxon c1 = TestEntityGenerator.newTaxon("child-1");
		c1.setParentKey(parent.getKey());
		mapper().create(c1);

		Taxon c2 = TestEntityGenerator.newTaxon("child-2");
		c2.setParentKey(parent.getKey());
		mapper().create(c2);

		Taxon c3 = TestEntityGenerator.newTaxon("child-3");
		c3.setParentKey(parent.getKey());
		mapper().create(c3);

		commit();

		List<Taxon> res = mapper().children(parent.getKey(), new Page(0, 5));
		assertEquals("01", 3, res.size());
		assertEquals("02", c1, res.get(0));
		assertEquals("03", c2, res.get(1));
		assertEquals("04", c3, res.get(2));

	}

}