package org.col.db.mapper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import org.col.api.Taxon;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
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
		t.setAccordingTo("Foo");
		t.setAccordingToDate(LocalDate.of(2010, 11, 24));
		t.setDataset(D1);
		t.setDatasetUrl(URI.create("http://foo.com"));
		t.setFossil(true);
		t.setId("taxon-test0");
		t.setLifezones(EnumSet.of(Lifezone.FRESHWATER, Lifezone.BRACKISH));
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