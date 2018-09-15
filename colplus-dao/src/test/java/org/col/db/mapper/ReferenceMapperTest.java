package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Sets;
import org.col.api.RandomUtils;
import org.col.api.model.CslData;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;

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
		Reference r2 = mapper().get(r1.getDatasetKey(), r1.getId());
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
		// list is sorted by id. From apple we get 2 records for dataset 11 that sort last:
		//r10001
		//r10002
		//r10003
		//r10004
		//r10005
		//ref-1
		//ref-1b
		in.add(REF1);
		in.add(REF2);
		List<Reference> out = mapper().list(DATASET11.getKey(), new Page());
		assertEquals(7, out.size());
		assertEquals(in, out);
	}

	@Test
	public void listByIds() {
		assertEquals(2, mapper().listByIds(11, Sets.newHashSet("ref-1","ref-1b")).size());
		assertEquals(1, mapper().listByIds(11, Sets.newHashSet("ref-1","ref-2")).size());
		assertEquals(1, mapper().listByIds(11, Sets.newHashSet("ref-1b")).size());
		assertEquals(0, mapper().listByIds(12, Sets.newHashSet("ref-1b")).size());
		assertEquals(1, mapper().listByIds(12, Sets.newHashSet("ref-2")).size());
	}

	private static Reference create() throws Exception {
		return newReference();
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