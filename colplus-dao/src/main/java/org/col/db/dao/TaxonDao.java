package org.col.db.dao;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.NameAccordingTo;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ResultPage;
import org.col.api.model.Synonym;
import org.col.api.model.Synonymy;
import org.col.api.model.Taxon;
import org.col.api.model.TaxonInfo;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.DistributionMapper;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.ReferenceMapper;
import org.col.db.mapper.SynonymMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.VernacularNameMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaxonDao {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);

  private final SqlSession session;
  private final TaxonMapper tMapper;
  private final NameMapper nMapper;
  private final SynonymMapper sMapper;
  private final ReferenceMapper rMapper;
  private final VernacularNameMapper vMapper;
  private final DistributionMapper dMapper;

  public TaxonDao(SqlSession sqlSession) {
    this.session = sqlSession;
    tMapper = session.getMapper(TaxonMapper.class);
    nMapper = session.getMapper(NameMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
    rMapper = session.getMapper(ReferenceMapper.class);
    vMapper = session.getMapper(VernacularNameMapper.class);
    dMapper = session.getMapper(DistributionMapper.class);
  }

  public ResultPage<Taxon> list(Integer datasetKey, Boolean root, Page page) {
    Page p = page == null ? new Page() : page;
    Boolean r = root == null ? Boolean.FALSE : root;
    int total = tMapper.count(datasetKey, r);
    List<Taxon> result = tMapper.list(datasetKey, r, p);
    return new ResultPage<>(p, total, result);
  }

  public Integer lookupKey(String id, int datasetKey) {
    return tMapper.lookupKey(id, datasetKey);
  }

  public Taxon get(int datasetKey, Integer key) {
    if (key == null) return null;
    return tMapper.get(datasetKey, key);
  }

  public Taxon get(String id, int datasetKey) {
    return get(datasetKey, lookupKey(id, datasetKey));
  }

  public List<Synonym> getSynonyms(int datasetKey, int nameKey) {
    return sMapper.listByName(datasetKey, nameKey);
  }

  public Synonym getSynonym(String ID, int datasetKey) {
    Integer nameKey = nMapper.lookupKey(ID, datasetKey);
    if (nameKey != null) {
      List<Synonym> syny = sMapper.listByName(datasetKey, nameKey);
      if (syny.isEmpty()) {
        return null;
      } else if (syny.size() > 1) {
        LOG.debug("Multiple synonyms found for name ID {}", ID);
      }
      return syny.get(0);
    }
    return null;
  }

  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(Taxon taxon) {
    return getSynonymy(taxon.getDatasetKey(), taxon.getKey());
  }

  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(int datasetKey, int taxonKey) {
    Name accName = nMapper.getByTaxon(taxonKey);
    Synonymy syn = new Synonymy();
    // get all synonyms and misapplied name
    // they come ordered by status, then homotypic group so its easy to arrange them
    List<Name> group = Lists.newArrayList();
    for (Synonym s : sMapper.listByTaxon(datasetKey, taxonKey)) {
      if (TaxonomicStatus.MISAPPLIED == s.getStatus()) {
        syn.addMisapplied(new NameAccordingTo(s.getName(), s.getAccordingTo()));
      } else {
        if (accName.getHomotypicNameKey().equals(s.getName().getHomotypicNameKey())) {
          syn.getHomotypic().add(s.getName());
        } else {
          if (!group.isEmpty()
              && !group.get(0).getHomotypicNameKey().equals(s.getName().getHomotypicNameKey())) {
            // new heterotypic group
            syn.addHeterotypicGroup(group);
            group = Lists.newArrayList();
          }
          // add to group
          group.add(s.getName());
        }
      }
    }
    if (!group.isEmpty()) {
      syn.addHeterotypicGroup(group);
    }

    return syn;
  }

  public List<Taxon> getClassification(Taxon taxon) {
    return getClassification(taxon.getDatasetKey(), taxon.getKey());
  }

  public List<Taxon> getClassification(int datasetKey, int key) {
    return tMapper.classification(datasetKey, key);
  }

  public ResultPage<Taxon> getChildren(int datasetKey, int key, Page page) {
    Page p = page == null ? new Page() : page;
    int total = tMapper.countChildren(datasetKey, key);
    List<Taxon> result = tMapper.children(datasetKey, key, p);
    return new ResultPage<>(p, total, result);
  }

  public void create(Taxon taxon) {
    tMapper.create(taxon);
  }

  public TaxonInfo getTaxonInfo(int datasetKey, int key) {
    return getTaxonInfo(tMapper.get(datasetKey, key));
  }

  public TaxonInfo getTaxonInfo(final Taxon taxon) {
    // main taxon object
    if (taxon == null) {
      return null;
    }

    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    info.setTaxonReferences(rMapper.listByTaxon(taxon.getDatasetKey(), taxon.getKey()));

    // vernaculars
    info.setVernacularNames(vMapper.listByTaxon(taxon.getDatasetKey(), taxon.getKey()));

    // distributions
    info.setDistributions(dMapper.listByTaxon(taxon.getDatasetKey(), taxon.getKey()));

    // all reference keys so we can select their details at the end
    Set<Integer> refKeys = new HashSet<>();
    refKeys.add(taxon.getName().getPublishedInKey());
    refKeys.addAll(info.getTaxonReferences());
    info.getDistributions().forEach(d -> refKeys.addAll(d.getReferenceKeys()));
    info.getVernacularNames().forEach(d -> refKeys.addAll(d.getReferenceKeys()));
    // make sure we did not add null by accident
    refKeys.remove(null);

    if (!refKeys.isEmpty()) {
      List<Reference> refs = rMapper.listByKeys(taxon.getDatasetKey(), refKeys);
      info.addReferences(refs);
    }

    return info;
  }

}
