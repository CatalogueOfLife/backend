package org.col.dao;

import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Distribution;
import org.col.api.Page;
import org.col.api.PagedReference;
import org.col.api.ResultPage;
import org.col.api.Taxon;
import org.col.api.TaxonInfo;
import org.col.api.VernacularName;
import org.col.db.KeyNotFoundException;
import org.col.db.NotInDatasetException;
import org.col.db.mapper.DistributionMapper;
import org.col.db.mapper.ReferenceMapper;
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

  public ResultPage<Taxon> list(Integer datasetKey, Page page) {
    Page p = page == null ? new Page() : page;
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    int total = mapper.count(datasetKey);
    List<Taxon> result = mapper.list(datasetKey, p);
    return new ResultPage<>(p, total, result);
  }

  public int lookupKey(String id, int datasetKey) throws NotInDatasetException {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    Integer key = mapper.lookupKey(id, datasetKey);
    if (key == null) {
      throw new NotInDatasetException(Taxon.class, datasetKey, id);
    }
    return key;
  }

  public Taxon get(int key) {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    Taxon result = mapper.get(key);
    if (result == null) {
      throw new KeyNotFoundException(Taxon.class, key);
    }
    return result;
  }

  public List<Taxon> getClassification(int key) {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    return mapper.classification(key);
  }

  public List<Taxon> getChildren(int key, Page page) {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    return mapper.children(key, page);
  }

  public void create(Taxon taxon) {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    mapper.create(taxon);
  }

  public TaxonInfo getTaxonInfo(int key) {

    TaxonMapper tMapper = session.getMapper(TaxonMapper.class);
    Taxon taxon = tMapper.get(key);
    if (taxon == null) {
      throw new KeyNotFoundException(Taxon.class, key);
    }
    TaxonInfo info = new TaxonInfo();

    info.setTaxon(taxon);

    VernacularNameMapper vMapper = session.getMapper(VernacularNameMapper.class);
    List<VernacularName> vernaculars = vMapper.listByTaxon(taxon.getKey());
    info.setVernacularNames(vernaculars);

    DistributionMapper dMapper = session.getMapper(DistributionMapper.class);
    List<Distribution> distributions = dMapper.listByTaxon(taxon.getKey());
    info.setDistributions(distributions);

    ReferenceMapper rMapper = session.getMapper(ReferenceMapper.class);

    List<PagedReference> refs = rMapper.listByTaxon(key);
    info.addReferences(refs);
    taxon.createReferences(refs);

    refs = rMapper.listByVernacularNamesOfTaxon(key);
    info.addReferences(refs);
    for (VernacularName v : vernaculars) {
      v.createReferences(refs);
    }

    refs = rMapper.listByDistributionOfTaxon(key);
    info.addReferences(refs);
    for (Distribution d : distributions) {
      d.createReferences(refs);
    }

    return info;
  }

}
