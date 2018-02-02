package org.col.db.dao;

import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Distribution;
import org.col.api.model.Page;
import org.col.api.model.PagedReference;
import org.col.api.model.ResultPage;
import org.col.api.model.Taxon;
import org.col.api.model.TaxonInfo;
import org.col.api.model.VerbatimRecord;
import org.col.api.model.VernacularName;
import org.col.db.KeyNotFoundException;
import org.col.db.NotInDatasetException;
import org.col.db.mapper.DistributionMapper;
import org.col.db.mapper.ReferenceMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.col.db.mapper.VernacularNameMapper;

public class TaxonDao {

  private final SqlSession session;

  public TaxonDao(SqlSession sqlSession) {
    this.session = sqlSession;
  }

  public ResultPage<Taxon> list(Integer datasetKey, Boolean root, Integer nameKey, Page page) {
    Page p = page == null ? new Page() : page;
    Boolean r = root == null ? Boolean.FALSE : root;
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    int total = mapper.count(datasetKey, r, nameKey);
    List<Taxon> result = mapper.list(datasetKey, r, nameKey, p);
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

  public ResultPage<Taxon> getChildren(int key, Page page) {
    Page p = page == null ? new Page() : page;
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    int total = mapper.countChildren(key);
    List<Taxon> result = mapper.children(key, p);
    return new ResultPage<>(p, total, result);
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

  public VerbatimRecord getVerbatim(int key) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.getByTaxon(key);
  }
}
