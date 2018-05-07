package org.col.db.dao;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.NotFoundException;
import org.col.db.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TaxonDao {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);

  private final SqlSession session;
  private final TaxonMapper tMapper;
  private final NameMapper nMapper;
  private final SynonymMapper sMapper;
  private final ReferenceMapper rMapper;
  private final VernacularNameMapper vMapper;
  private final DistributionMapper dMapper;
  private final VerbatimRecordMapper vbMapper;

  public TaxonDao(SqlSession sqlSession) {
    this.session = sqlSession;
    tMapper = session.getMapper(TaxonMapper.class);
    nMapper = session.getMapper(NameMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
    rMapper = session.getMapper(ReferenceMapper.class);
    vMapper = session.getMapper(VernacularNameMapper.class);
    dMapper = session.getMapper(DistributionMapper.class);
    vbMapper = session.getMapper(VerbatimRecordMapper.class);
  }

  public ResultPage<Taxon> list(Integer datasetKey, Boolean root, Page page) {
    Page p = page == null ? new Page() : page;
    Boolean r = root == null ? Boolean.FALSE : root;
    int total = tMapper.count(datasetKey, r);
    List<Taxon> result = tMapper.list(datasetKey, r, p);
    return new ResultPage<>(p, total, result);
  }

  public int lookupKey(String id, int datasetKey) throws NotFoundException {
    Integer key = tMapper.lookupKey(id, datasetKey);
    if (key == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return key;
  }

  public Taxon get(int key) {
    Taxon result = tMapper.get(key);
    if (result == null) {
      throw NotFoundException.keyNotFound(Taxon.class, key);
    }
    return result;
  }

  public Taxon get(String id, int datasetKey) {
    return get(lookupKey(id, datasetKey));
  }


  public List<Synonym> getSynonyms(int nameKey) {
    return sMapper.listByName(nameKey);
  }

  public Synonym getSynonym(String ID, int datasetKey) {
    Integer nameKey = nMapper.lookupKey(ID, datasetKey);
    if (nameKey != null) {
      List<Synonym> syny = sMapper.listByName(nameKey);
      if (syny.isEmpty()) {
        return null;
      } else if (syny.size() > 1){
        LOG.debug("Multiple synonyms found for name ID {}", ID);
      }
      return syny.get(0);
    }
    return null;
  }

  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(int taxonKey) {
    Name accName = nMapper.getByTaxon(taxonKey);
    Synonymy syn = new Synonymy();
    // get all synonyms and misapplied name
    // they come ordered by status, then homotypic group so its easy to arrange them
    List<Name> group = Lists.newArrayList();
    for (Synonym s : sMapper.listByTaxon(taxonKey)) {
      if (TaxonomicStatus.MISAPPLIED == s.getStatus()) {
        syn.addMisapplied(new NameAccordingTo(s.getName(), s.getAccordingTo()));
      } else {
        if (accName.getHomotypicNameKey().equals(s.getName().getHomotypicNameKey())) {
          syn.getHomotypic().add(s.getName());
        } else {
          if (!group.isEmpty() && !group.get(0).getHomotypicNameKey().equals(s.getName().getHomotypicNameKey())) {
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

  public List<Taxon> getClassification(int key) {
    return tMapper.classification(key);
  }

  public ResultPage<Taxon> getChildren(int key, Page page) {
    Page p = page == null ? new Page() : page;
    int total = tMapper.countChildren(key);
    List<Taxon> result = tMapper.children(key, p);
    return new ResultPage<>(p, total, result);
  }

  public void create(Taxon taxon) {
    tMapper.create(taxon);
  }

  public TaxonInfo getTaxonInfo(int key) {
    // main taxon object
    Taxon taxon = tMapper.get(key);
    if (taxon == null) {
      throw NotFoundException.keyNotFound(Taxon.class, key);
    }

    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    info.setTaxonReferences(rMapper.listByTaxon(key));

    // vernaculars
    info.setVernacularNames(vMapper.listByTaxon(taxon.getKey()));

    // distributions
    info.setDistributions(dMapper.listByTaxon(taxon.getKey()));

    // all reference keys so we can select their details at the end
    Set<Integer> refKeys = new HashSet<>();
    refKeys.add(taxon.getName().getPublishedInKey());
    refKeys.addAll(info.getTaxonReferences());
    info.getDistributions().forEach(d -> refKeys.addAll(d.getReferenceKeys()));
    info.getVernacularNames().forEach(d -> refKeys.addAll(d.getReferenceKeys()));
    // make sure we did not add null by accident
    refKeys.remove(null);

    if (!refKeys.isEmpty()) {
      List<Reference> refs = rMapper.listByKeys(refKeys);
      info.addReferences(refs);
    }

    return info;
  }

  public VerbatimRecord getVerbatim(int key) {
    return vbMapper.getByTaxon(key);
  }
}
