package org.col.db.mapper;

import org.col.api.Name;
import org.col.api.NameAct;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.Origin;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import java.util.List;

import static org.col.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
		List<NameAct> nas = mapper().listByName(DATASET1.getKey(), NAME1.getKey());
		/*
		 * NB We have one pre-inserted (squirrels.sql) NameAct record associated with
		 * NAME1; one of the records inserted here is _not_ associated with NAME1, so we
		 * should have 3 NameAct records associated with NAME1
		 */
		assertEquals("01", 3, nas.size());
	}

	private Name createName(String id, String name){
    Name n = new Name();
    n.setDatasetKey(DATASET1.getKey());
    n.setId(id);
    n.setScientificName(name);
    n.setType(NameType.SCIENTIFIC);
    n.setRank(Rank.UNRANKED);
    n.setOrigin(Origin.SOURCE);
    return n;
  }

  private NameAct createAct(int nameKey,  NomActType type){
    NameAct act = new NameAct();
    act.setDatasetKey(DATASET1.getKey());
    act.setNameKey(nameKey);
    act.setType(type);
    return act;
  }

	@Test
	public void testListByHomotypicGroup() throws Exception {

		NameMapper nameMapper = initMybatisRule.getMapper(NameMapper.class);

		// Create basionym with 2 name acts

		Name basionym = createName("foo-bar", "Foo bar");
		nameMapper.create(basionym);

		NameAct nameAct = createAct(basionym.getKey(), NomActType.DESCRIPTION);
		mapper().create(nameAct);

    nameAct = createAct(basionym.getKey(), NomActType.TYPIFICATION);
		mapper().create(nameAct);

		// Create name referencing basionym, also with 2 name acts

    Name name = createName("foo-new", "Foo new");
		name.setBasionymKey(basionym.getKey());
		nameMapper.create(name);

    nameAct = createAct(name.getKey(), NomActType.DESCRIPTION);
		mapper().create(nameAct);

    nameAct = createAct(name.getKey(), NomActType.TYPIFICATION);
		mapper().create(nameAct);

		// Create another name referencing basionym, with 1 name act

    name = createName("foo-too", "Foo too");
		name.setBasionymKey(basionym.getKey());
		nameMapper.create(name);

    nameAct = createAct(name.getKey(), NomActType.DESCRIPTION);
		mapper().create(nameAct);

		commit();

		// So total size of homotypic group is 2 + 2 + 1 = 5

		Integer nameKey = nameMapper.lookupKey(DATASET1.getKey(), "foo-bar");
		List<NameAct> nas = mapper().listByHomotypicGroup(DATASET1.getKey(), nameKey);
		assertEquals("01", 5, nas.size());

		nameKey = nameMapper.lookupKey(DATASET1.getKey(), "foo-new");
		nas = mapper().listByHomotypicGroup(DATASET1.getKey(), nameKey);
		assertEquals("02", 5, nas.size());

		nameKey = nameMapper.lookupKey(DATASET1.getKey(), "foo-too");
		nas = mapper().listByHomotypicGroup(DATASET1.getKey(), nameKey);
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
