package org.col.db.mapper;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.col.db.TestEntityGenerator;
import org.col.api.model.Page;
import org.col.api.model.PagedReference;
import org.col.api.RandomUtils;
import org.col.api.model.Reference;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.col.db.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ReferenceMapperTest extends MapperTestBase<ReferenceMapper> {

	public ReferenceMapperTest() {
		super(ReferenceMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		Reference r1 = create();
		mapper().create(r1);
		commit();
		Reference r2 = mapper().get(r1.getKey());
		assertEquals(r1, r2);
	}

	@Test
	public void count() throws Exception {
		int i = mapper().count(DATASET1.getKey());
		// Just to make sure we understand our environment:
		// we start with 2 records in reference table, inserted through
		// squirrels, ONLY one of which belongs to DATASET1.
		assertEquals("01", 1, i);
		mapper().create(create());
		mapper().create(create());
		mapper().create(create());
		assertEquals("02", 4, mapper().count(DATASET1.getKey()));
	}

	@Test
	public void list() throws Exception {
		List<Reference> in = new ArrayList<>();
		in.add(create());
		in.add(create());
		in.add(create());
		in.add(create());
		in.add(create());
		mapper().create(in.get(0));
		mapper().create(in.get(1));
		mapper().create(in.get(2));
		mapper().create(in.get(3));
		mapper().create(in.get(4));
		// Skip first (pre-inserted) record:
		Page p = new Page(1, 3);
		List<Reference> out = mapper().list(DATASET1.getKey(), p);
		assertEquals("01", 3, out.size());
		assertTrue("02", in.get(0).equals(out.get(0)));
		assertTrue("03", in.get(1).equals(out.get(1)));
		assertTrue("04", in.get(2).equals(out.get(2)));
		p.next();
		out = mapper().list(DATASET1.getKey(), p);
		assertEquals("05", 2, out.size());
	}

	@Test
	public void listByTaxon() {
		List<PagedReference> refs = mapper().listByTaxon(1);
		assertEquals("01", 1, refs.size());
		assertEquals("02", "12", refs.get(0).getReferencePage());
		refs = mapper().listByTaxon(2);
		assertEquals("03", 2, refs.size());
		assertEquals("04", "100", refs.get(0).getReferencePage());
		assertEquals("05", "133", refs.get(1).getReferencePage());
	}

	@Test
	public void listByVernacularNamesOfTaxon() {
		List<PagedReference> refs = mapper().listByVernacularNamesOfTaxon(1);
		assertEquals("01", 2, refs.size());
		assertEquals("02", Integer.valueOf(1), refs.get(0).getReferenceForKey());
		assertEquals("03", Integer.valueOf(2), refs.get(1).getReferenceForKey());
		assertEquals("04", "145", refs.get(0).getReferencePage());
		assertEquals("05", "145", refs.get(1).getReferencePage());
	}

	@Test
	public void listByDistributionOfTaxon() {
		List<PagedReference> refs = mapper().listByDistributionOfTaxon(1);
		assertEquals("01", 3, refs.size());
	}

	@Test
	public void getPublishedIn() {
		PagedReference ref = mapper().getPublishedIn(NAME1.getKey());
		assertEquals("01", REF1, ref);
		assertEquals("02", "712", ref.getReferencePage());
	}

	private static Reference create() throws Exception {
		Reference ref = new Reference();
		ref.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
		ref.setId(RandomUtils.randomString(8));
		ref.setYear(1988);
		ref.setCsl(createCsl());
		return ref;
	}

	private static ObjectNode createCsl() {
		JsonNodeFactory factory = JsonNodeFactory.instance;
		ObjectNode csl = factory.objectNode();
		csl.put("title", RandomUtils.randomString(80));
		csl.put("container-title", RandomUtils.randomString(100));
		csl.put("publisher", "Springer");
		csl.put("year", "1988");
		csl.put("doi", "doi:10.1234/" + RandomUtils.randomString(20));
		return csl;
	}

}