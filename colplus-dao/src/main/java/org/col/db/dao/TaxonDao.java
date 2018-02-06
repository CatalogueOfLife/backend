package org.col.db.dao;

import com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.KeyNotFoundException;
import org.col.db.NotInDatasetException;
import org.col.db.mapper.*;

import java.util.List;
import java.util.Set;

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
    // main taxon object
    TaxonMapper tMapper = session.getMapper(TaxonMapper.class);
    Taxon taxon = tMapper.get(key);
    if (taxon == null) {
      throw new KeyNotFoundException(Taxon.class, key);
    }

    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    info.setTaxonReferences(tMapper.taxonReferences(key));

    // vernaculars
    VernacularNameMapper vMapper = session.getMapper(VernacularNameMapper.class);
    info.setVernacularNames(vMapper.listByTaxon(taxon.getKey()));


    // distributions
    DistributionMapper dMapper = session.getMapper(DistributionMapper.class);
    info.setDistributions(dMapper.listByTaxon(taxon.getKey()));

    // all reference keys so we can select their details at the end
    Set<Integer> refKeys = Sets.newHashSet();
    refKeys.addAll(info.getTaxonReferences());
    info.getDistributions().forEach(d -> refKeys.addAll(d.getReferenceKeys()));
    info.getVernacularNames().forEach(d -> refKeys.addAll(d.getReferenceKeys()));

    ReferenceMapper rMapper = session.getMapper(ReferenceMapper.class);
    if (!refKeys.isEmpty()) {
      List<Reference> refs = rMapper.listByKeys(refKeys);
      info.addReferences(refs);
    }

    return info;
  }

  public VerbatimRecord getVerbatim(int key) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.getByTaxon(key);
  }
}
