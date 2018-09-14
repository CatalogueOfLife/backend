package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Sets;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.CslData;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
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
		Reference r2 = mapper().get(r1.getDatasetKey(), r1.getKey());
		assertEquals(r1, r2);
	}

	@Test
	public void count() throws Exception {
		// we start with 3 records in reference table, inserted through
		// apple, only two of which belong to DATASET11.
		mapper().create(create());
		mapper().create(create());
		mapper().create(create());
		generateDatasetImport(DATASET11.getKey());
		commit();

		assertEquals(5, mapper().count(DATASET11.getKey()));
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
		// Skip first two (pre-inserted) record:
		Page p = new Page(2, 3);
		List<Reference> out = mapper().list(DATASET11.getKey(), p);
		assertEquals(3, out.size());
		assertTrue(in.get(0).equals(out.get(0)));
		assertTrue(in.get(1).equals(out.get(1)));
		assertTrue(in.get(2).equals(out.get(2)));
		p.next();
		out = mapper().list(DATASET11.getKey(), p);
		assertEquals(2, out.size());
	}

	@Test
	public void listByKeys() {
		assertEquals(2, mapper().listByKeys(11, Sets.newHashSet(1,2)).size());
		assertEquals(1, mapper().listByKeys(11, Sets.newHashSet(1,3)).size());
		assertEquals(1, mapper().listByKeys(11, Sets.newHashSet(2)).size());
		assertEquals(0, mapper().listByKeys(12, Sets.newHashSet(2)).size());
		assertEquals(1, mapper().listByKeys(12, Sets.newHashSet(3)).size());
	}

	private static Reference create() throws Exception {
		Reference ref = new Reference();
		ref.setDatasetKey(TestEntityGenerator.DATASET11.getKey());
		ref.setId(RandomUtils.randomString(8));
		ref.setYear(1988);
		ref.setCsl(createCsl());
		return ref;
	}

	private static CslData createCsl() {
	  CslData item = new CslData();
	  item.setTitle(RandomUtils.randomString(80));
	  item.setContainerTitle(RandomUtils.randomString(100));
	  item.setPublisher("Springer");
	  item.setYearSuffix("1988b");
	  item.setDOI("doi:10.1234/" + RandomUtils.randomString(20));
		return item;
	}

}