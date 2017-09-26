package org.col.db.mapper;

import static org.junit.Assert.assertEquals;

import org.col.api.Name;
import org.col.api.Taxon;
import org.junit.Test;

/**
 *
 */
public class TaxonMapperTest extends MapperTestBase<NameMapper> {

	public TaxonMapperTest() {
		super(NameMapper.class);
	}

	private Taxon create() throws Exception {
		Taxon t = new Taxon();
		return t;
	}

	@Test
	public void roundtrip() throws Exception {
		Name n1 = create();
		n1.setKey("sk1");
		mapper.insert(n1);

		commit();

		Name n1b = mapper.get(d1.getKey(), n1.getKey());
		assertEquals(n1, n1b);

		Name n1c = mapper.getByInternalKey(n1.getKeyInternal());
		assertEquals(n1, n1c);

		// now with basionym
		Name n2 = create();
		n2.setKey("sk2");
		n2.setKey("sk2");
		n2.setOriginalName(n1);
		n2.setOriginalName(n1);
		mapper.insert(n2);

		commit();

		// we use a new instance of n1 with just the keys for the equality tests
		n1 = new Name();
		n1.setKey(n2.getOriginalName().getKey());
		n1.setKeyInternal(n2.getOriginalName().getKeyInternal());
		n2.setOriginalName(n1);

		Name n2b = mapper.get(d1.getKey(), n2.getKey());
		assertEquals(n2, n2b);

		Name n2c = mapper.getByInternalKey(n2.getKeyInternal());
		assertEquals(n2, n2c);

	}

}