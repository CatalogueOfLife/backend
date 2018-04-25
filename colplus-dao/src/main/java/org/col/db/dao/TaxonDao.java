package org.col.db.dao;

import com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.NotFoundException;
import org.col.db.mapper.*;

import java.util.List;
import java.util.Set;

public class TaxonDao {

  private final SqlSession session;

  public TaxonDao(SqlSession sqlSession) {
    this.session = sqlSession;
  }

  public ResultPage<Taxon> list(Integer datasetKey, Boolean root, Page page) {
    Page p = page == null ? new Page() : page;
    Boolean r = root == null ? Boolean.FALSE : root;
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    int total = mapper.count(datasetKey, r);
    List<Taxon> result = mapper.list(datasetKey, r, p);
    return new ResultPage<>(p, total, result);
  }

  public int lookupKey(String id, int datasetKey) throws NotFoundException {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    Integer key = mapper.lookupKey(id, datasetKey);
    if (key == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return key;
  }

  public Taxon get(int key) {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    Taxon result = mapper.get(key);
    if (result == null) {
      throw NotFoundException.keyNotFound(Taxon.class, key);
    }
    return result;
  }

  public Taxon get(String id, int datasetKey) {
    return get(lookupKey(id, datasetKey));
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
      throw NotFoundException.keyNotFound(Taxon.class, key);
    }

    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    ReferenceMapper rMapper = session.getMapper(ReferenceMapper.class);
    info.setTaxonReferences(rMapper.listByTaxon(key));

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
