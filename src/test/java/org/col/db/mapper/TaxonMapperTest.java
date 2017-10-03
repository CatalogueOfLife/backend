package org.col.db.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.col.api.Name;
import org.col.api.Page;
import org.col.api.Taxon;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 *
 */
public class TaxonMapperTest extends MapperTestBase<TaxonMapper> {

	public TaxonMapperTest() {
		super(TaxonMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		Taxon in = create("t1");
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		Taxon out = mapper().get(D1.getKey(), in.getId());
		assertTrue(in.equalsShallow(out));
	}

	@Test
	public void count() throws Exception {
		int i = mapper().count(D1.getKey());
		// Just to make sure we understand our environment
		// 2 Taxa pre-inserted through InitMybatisRule.squirrels()
		assertEquals(2, i);
		mapper().create(create("t2"));
		mapper().create(create("t3"));
		mapper().create(create("t4"));
		assertEquals(5, mapper().count(D1.getKey()));
	}

	@Test
	public void list() throws Exception {
		List<Taxon> taxa = new ArrayList<>();
		taxa.add(create("t1"));
		taxa.add(create("t2"));
		taxa.add(create("t3"));
		taxa.add(create("t4"));
		taxa.add(create("t5"));
		taxa.add(create("t6"));
		taxa.add(create("t7"));
		taxa.add(create("t8"));
		taxa.add(create("t9"));
		for(Taxon t : taxa) {
			mapper().create(t);
		}
		commit();

    // get first page
    Page p = new Page(0,3);

    List<Taxon> res = mapper().list(D1.getKey(), p);
    assertEquals(3, res.size());
    // First 2 taxa in dataset D1 are pre-inserted taxa:
    assertTrue(TAXON1.equalsShallow(res.get(0)));
    assertTrue(TAXON2.equalsShallow(res.get(1)));
    assertTrue(taxa.get(0).equalsShallow(res.get(2)));
    
		p.next();
		res = mapper().list(D1.getKey(), p);
		assertEquals(3, res.size());
		assertTrue(taxa.get(1).equalsShallow(res.get(0)));
		assertTrue(taxa.get(2).equalsShallow(res.get(1)));
		assertTrue(taxa.get(3).equalsShallow(res.get(2)));

	}

	private static Taxon create(String id) throws Exception {
		Taxon t = create();
		t.setId(id);
		return t;
	}

	private static Taxon create() throws Exception {
		Taxon t = new Taxon();
		t.setAccordingTo("Foo");
		t.setAccordingToDate(LocalDate.of(2010, 11, 24));
		t.setDataset(D1);
		t.setDatasetUrl(URI.create("http://foo.com"));
		t.setFossil(true);
		t.setLifezones(EnumSet.of(Lifezone.BRACKISH, Lifezone.FRESHWATER, Lifezone.TERRESTRIAL));
		t.setName(NAME1);
		t.setOrigin(Origin.SOURCE);
		t.setParent(TAXON1);
		t.setStatus(TaxonomicStatus.ACCEPTED);
		t.setRank(Rank.CLASS);
		t.setRecent(true);
		t.setRemarks("Foo == Bar");
		t.setSpeciesEstimate(81);
		t.setSpeciesEstimateReference(REF1);
		return t;
	}

}