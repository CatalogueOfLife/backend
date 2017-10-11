package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Taxon;
import org.col.api.TaxonInfo;
import org.col.api.VernacularName;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.VernacularNameMapper;

public class TaxonDao {

	private final SqlSession session;

	public TaxonDao(SqlSession sqlSession) {
		this.session = sqlSession;
	}

	public Taxon get(int datasetKey, String id) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		return mapper.get(datasetKey, id);
	}

	public List<VernacularName> getVernacularNames(Integer datasetKey, String taxonId) {
		VernacularNameMapper mapper = session.getMapper(VernacularNameMapper.class);
		return mapper.getVernacularNames(datasetKey, taxonId);
	}

	public TaxonInfo getTaxonInfo(int datasetKey, String id) {
		TaxonInfo info = new TaxonInfo();
		TaxonMapper tMapper = session.getMapper(TaxonMapper.class);
		Taxon taxon = tMapper.get(datasetKey, id);
		info.setTaxon(taxon);
		VernacularNameMapper vMapper = session.getMapper(VernacularNameMapper.class);
		List<VernacularName> vernaculars = vMapper.getVernacularNamesByTaxonKey(taxon.getKey());
		info.setVernacularNames(vernaculars);
		return info;
	}

}
