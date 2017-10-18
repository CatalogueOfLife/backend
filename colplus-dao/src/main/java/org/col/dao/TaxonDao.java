package org.col.dao;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Page;
import org.col.api.PagingResultSet;
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

	public int count(int datasetKey) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		return mapper.count(datasetKey);
	}

	public PagingResultSet<Taxon> list(int datasetKey, @Nullable Page page) {
		Page p = page == null ? new Page() : page;
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		int total = mapper.count(datasetKey);
		List<Taxon> result = mapper.list(datasetKey, p);
		return new PagingResultSet<>(p, total, result);
	}

	public Taxon getByKey(int key) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		return mapper.getByKey(key);
	}

	public Taxon get(int datasetKey, String id) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		return mapper.get(datasetKey, id);
	}

	public void create(Taxon taxon) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		mapper.create(taxon);
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
