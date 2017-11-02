package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.*;
import org.col.db.NotFoundException;
import org.col.db.mapper.DistributionMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.VernacularNameMapper;

import javax.annotation.Nullable;
import java.util.List;

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

	public Taxon get(int key) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		return mapper.get(key);
	}

	public Taxon get(int datasetKey, String id) {
		return get(lookup(datasetKey, id));
	}

	public void create(Taxon taxon) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		mapper.create(taxon);
	}

  public TaxonInfo getTaxonInfo(int datasetKey, String id) {
	  return getTaxonInfo(lookup(datasetKey, id));
  }

	public TaxonInfo getTaxonInfo(int key) {
		TaxonInfo info = new TaxonInfo();

		TaxonMapper tMapper = session.getMapper(TaxonMapper.class);
		Taxon taxon = tMapper.get(key);
		info.setTaxon(taxon);

		VernacularNameMapper vMapper = session.getMapper(VernacularNameMapper.class);
		List<VernacularName> vernaculars = vMapper.getVernacularNamesByTaxonKey(taxon.getKey());
		info.setVernacularNames(vernaculars);

		DistributionMapper dMapper = session.getMapper(DistributionMapper.class);
		List<Distribution> distributions = dMapper.getDistributionsByTaxonKey(taxon.getKey());
		info.setDistributions(distributions);

		return info;
	}

  private int lookup(int datasetKey, String id) throws NotFoundException {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    Integer key = mapper.lookupKey(datasetKey, id);
    if (key == null) {
      throw new NotFoundException(Taxon.class, datasetKey, id);
    }
    return key;
  }

}
