package org.col.db.mapper;

import static org.col.TestEntityGenerator.DATASET1;
import static org.col.TestEntityGenerator.NAME1;
import static org.col.TestEntityGenerator.NAME2;
import static org.col.TestEntityGenerator.REF1;
import static org.col.TestEntityGenerator.REF2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.col.api.Name;
import org.col.api.NameAct;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.NomStatus;
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
		NameAct in = newNameAct1();
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		NameAct out = mapper().get(in.getKey());
		// assertTrue(in.equalsShallow(out));
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
		basionym.setDatasetKey(DATASET1.getKey());
		basionym.setId("foo-bar");
		basionym.setScientificName("Foo bar");
		nameMapper.create(basionym);

		NameAct nameAct;

		nameAct = new NameAct();
		nameAct.setDatasetKey(DATASET1.getKey());
		nameAct.setNameKey(basionym.getKey());
		nameAct.setType(NomActType.DESCRIPTION);
		mapper().create(nameAct);

		nameAct = new NameAct();
		nameAct.setDatasetKey(DATASET1.getKey());
		nameAct.setNameKey(basionym.getKey());
		nameAct.setType(NomActType.TYPIFICATION);
		mapper().create(nameAct);

		// Create name referencing basionym, also with 2 name acts

		Name name = new Name();
		name.setDatasetKey(DATASET1.getKey());
		name.setId("foo-new");
		name.setScientificName("Foo new");
		name.setBasionym(basionym);
		nameMapper.create(name);

		nameAct = new NameAct();
		nameAct.setDatasetKey(DATASET1.getKey());
		nameAct.setNameKey(name.getKey());
		nameAct.setType(NomActType.DESCRIPTION);
		mapper().create(nameAct);

		nameAct = new NameAct();
		nameAct.setDatasetKey(DATASET1.getKey());
		nameAct.setNameKey(name.getKey());
		nameAct.setType(NomActType.TYPIFICATION);
		mapper().create(nameAct);

		// Create another name referencing basionym, with 1 name act

		name = new Name();
		name.setDatasetKey(DATASET1.getKey());
		name.setId("foo-too");
		name.setScientificName("Foo too");
		name.setBasionym(basionym);
		nameMapper.create(name);

		nameAct = new NameAct();
		nameAct.setDatasetKey(DATASET1.getKey());
		nameAct.setNameKey(name.getKey());
		nameAct.setType(NomActType.DESCRIPTION);
		mapper().create(nameAct);

		commit();

		// So total size of homotypic group is 2 + 2 + 1 = 5

		List<NameAct> nas = mapper().listByHomotypicGroup(DATASET1.getKey(), "foo-bar");
		assertEquals("01", 5, nas.size());

		nas = mapper().listByHomotypicGroup(DATASET1.getKey(), "foo-new");
		assertEquals("02", 5, nas.size());

		nas = mapper().listByHomotypicGroup(DATASET1.getKey(), "foo-too");
		assertEquals("03", 5, nas.size());
	}

	private static NameAct newNameAct1() {
		NameAct na = new NameAct();
		na.setDatasetKey(DATASET1.getKey());
		na.setType(NomActType.DESCRIPTION);
		na.setStatus(NomStatus.REPLACEMENT);
		na.setNameKey(NAME1.getKey());
		na.setRelatedNameKey(NAME2.getKey());
		na.setReferenceKey(REF1.getKey());
		na.setReferencePage("12");
		return na;
	}

	private static NameAct newNameAct2() {
		NameAct na = new NameAct();
		na.setDatasetKey(DATASET1.getKey());
		na.setType(NomActType.DESCRIPTION);
		na.setStatus(NomStatus.REPLACEMENT);
		na.setNameKey(NAME2.getKey());
		na.setRelatedNameKey(null);
		na.setReferenceKey(REF2.getKey());
		na.setReferencePage("9");
		return na;
	}

}
