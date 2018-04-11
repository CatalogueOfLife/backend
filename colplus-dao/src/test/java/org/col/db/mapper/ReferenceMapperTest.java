package org.col.db.mapper;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import org.col.api.RandomUtils;
import org.col.api.model.CslItemData;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.vocab.Issue;
import org.col.db.mapper.temp.ReferenceWithPage;
import org.col.api.TestEntityGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.col.api.TestEntityGenerator.*;
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
		// apple, ONLY one of which belongs to DATASET1.
		assertEquals(1, i);
		mapper().create(create());
		mapper().create(create());
		mapper().create(create());
		assertEquals(4, mapper().count(DATASET1.getKey()));
	}

	@Test
	public void list() throws Exception {
		List<Reference> in = new ArrayList<>();
		in.add(create());
		in.add(create());
		in.add(create());
		in.add(create());
		in.add(create());
		for (Reference r : in) {
		  mapper().create(r);
    }
    commit();
		// Skip first (pre-inserted) record:
		Page p = new Page(1, 3);
		List<Reference> out = mapper().list(DATASET1.getKey(), p);
		assertEquals(3, out.size());
		assertTrue(in.get(0).equals(out.get(0)));
		assertTrue(in.get(1).equals(out.get(1)));
		assertTrue(in.get(2).equals(out.get(2)));
		p.next();
		out = mapper().list(DATASET1.getKey(), p);
		assertEquals(2, out.size());
	}

	@Test
	public void listByKeys() {
		List<Reference> refs = mapper().listByKeys(Sets.newHashSet(1,2));
		assertEquals(2, refs.size());
	}

	@Test
	public void getPublishedIn() {
		ReferenceWithPage ref = mapper().getPublishedIn(NAME1.getKey());
		assertEquals(REF1, ref.getReference());
		assertEquals("712", ref.getPage());
	}

	private static Reference create() throws Exception {
		Reference ref = new Reference();
		ref.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
		ref.setId(RandomUtils.randomString(8));
		ref.setYear(1988);
		ref.setCsl(createCsl());
		ref.addIssue(Issue.REFERENCE_ID_INVALID);
    ref.addIssue(Issue.RELATIONSHIP_MISSING);
		return ref;
	}

	private static CslItemData createCsl() {
	  CslItemData item = new CslItemData();
	  item.setTitle(RandomUtils.randomString(80));
	  item.setContainerTitle(RandomUtils.randomString(100));
	  item.setPublisher("Springer");
	  item.setYearSuffix("1988b");
	  item.setDOI("doi:10.1234/" + RandomUtils.randomString(20));
		return item;
	}

}