package org.col.db.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.col.api.Page;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.NomStatus;
import org.col.api.NameAct;
import org.col.dao.DaoTestUtil;
import org.junit.Test;

/**
 *
 */
public class NameActMapperTest extends MapperTestBase<NameActMapper> {

	public NameActMapperTest() {
		super(NameActMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		NameAct in = newNameAct();
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		NameAct out = mapper().getByKey(in.getKey());
		assertTrue(in.equalsShallow(out));
	}

	private static NameAct newNameAct() {
		NameAct na = new NameAct();
		na.setDataset(DaoTestUtil.DATASET1);
		na.setType(NomActType.DESCRIPTION);
		na.setStatus(NomStatus.REPLACEMENT);
		na.setName(DaoTestUtil.NAME1);
		na.setRelatedName(DaoTestUtil.NAME2);
		na.setReference(DaoTestUtil.REF1);
		na.setReferencePage("12");
		return na;
	}

}
