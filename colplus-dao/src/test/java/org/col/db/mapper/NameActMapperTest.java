package org.col.db.mapper;

import org.col.api.Name;
import org.col.api.NameAct;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.NomStatus;
import org.junit.Test;

import java.util.List;

import static org.col.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
public class NameActMapperTest extends MapperTestBase<NameActMapper> {

	public NameActMapperTest() {
		super(NameActMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		NameAct in = newNameAct1();
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		NameAct out = mapper().getByKey(in.getKey());
		assertTrue(in.equalsShallow(out));
	}

	@Test
	public void testListByName() throws Exception {
		mapper().create(newNameAct1());
		mapper().create(newNameAct1());
		mapper().create(newNameAct2());
		commit();
		List<NameAct> nas = mapper().listByName(DATASET1.getKey(), NAME1.getId());
		/*
		 * NB We have one pre-inserted (squirrels.sql) NameAct record associated with
		 * NAME1; one of the records inserted here is _not_ associated with NAME1, so we
		 * should have 3 NameAct records associated with NAME1
		 */
		assertEquals("01", 3, nas.size());
	}

	@Test
	public void testListByHomotypicGroup() throws Exception {
		
		NameMapper nameMapper = initMybatisRule.getMapper(NameMapper.class);
		
		// Create basionym with 2 name acts
		
		Name basionym = new Name();
		basionym.setDataset(DATASET1);
		basionym.setId("foo-bar");
		basionym.setScientificName("Foo bar");
		nameMapper.create(basionym);
		
		NameAct nameAct;
		
		nameAct = new NameAct();
		nameAct.setDataset(DATASET1);
		nameAct.setName(basionym);
		nameAct.setType(NomActType.DESCRIPTION);
		mapper().create(nameAct);
		
		nameAct = new NameAct();
		nameAct.setDataset(DATASET1);
		nameAct.setName(basionym);
		nameAct.setType(NomActType.TYPIFICATION);
		mapper().create(nameAct);
		
		// Create name referencing basionym, also with 2 name acts
		
 		Name name = new Name();
		name.setDataset(DATASET1);
		name.setId("foo-new");
		name.setScientificName("Foo new");
		name.setBasionym(basionym);
		nameMapper.create(name);

		nameAct = new NameAct();
		nameAct.setDataset(DATASET1);
		nameAct.setName(name);
		nameAct.setType(NomActType.DESCRIPTION);
		mapper().create(nameAct);
		
		nameAct = new NameAct();
		nameAct.setDataset(DATASET1);
		nameAct.setName(name);
		nameAct.setType(NomActType.TYPIFICATION);
		mapper().create(nameAct);
		
		// Create another name referencing basionym, with 1 name act			
		
		name = new Name();
		name.setDataset(DATASET1);
		name.setId("foo-too");
		name.setScientificName("Foo too");
		name.setBasionym(basionym);
		nameMapper.create(name);

		nameAct = new NameAct();
		nameAct.setDataset(DATASET1);
		nameAct.setName(name);
		nameAct.setType(NomActType.DESCRIPTION);
		mapper().create(nameAct);	

		commit();
		
		List<NameAct> nas = mapper().listByHomotypicGroup(DATASET1.getKey(), "foo-too");
		
		// 2 + 2 + 1
		assertEquals("01", 5, nas.size());
	}

	private static NameAct newNameAct1() {
		NameAct na = new NameAct();
		na.setDataset(DATASET1);
		na.setType(NomActType.DESCRIPTION);
		na.setStatus(NomStatus.REPLACEMENT);
		na.setName(NAME1);
		na.setRelatedName(NAME2);
		na.setReference(REF1);
		na.setReferencePage("12");
		return na;
	}

	private static NameAct newNameAct2() {
		NameAct na = new NameAct();
		na.setDataset(DATASET1);
		na.setType(NomActType.DESCRIPTION);
		na.setStatus(NomStatus.REPLACEMENT);
		na.setName(NAME2);
		na.setRelatedName(null);
		na.setReference(REF2);
		na.setReferencePage("9");
		return na;
	}

}
