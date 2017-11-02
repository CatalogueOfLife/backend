package org.col.dao;

import org.col.api.Taxon;
import org.col.api.TaxonInfo;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.VernacularNameMapper;
import org.junit.Test;

import static org.col.TestEntityGenerator.*;

public class TaxonDaoTest extends DaoTestBase {

	@Test
	public void testInfo() throws Exception {
		Taxon taxon = newTaxon("test-taxon");
		mapper(TaxonMapper.class).create(taxon);
		mapper(VernacularNameMapper.class).create(newVernacularName(taxon, "cat"));
		mapper(VernacularNameMapper.class).create(newVernacularName(taxon, "mouse"));
		mapper(VernacularNameMapper.class).create(newVernacularName(taxon, "dog"));
		session().commit();
		TaxonDao dao = new TaxonDao(session());
		TaxonInfo info = dao.getTaxonInfo(DATASET1.getKey(), taxon.getId());
	}

}
