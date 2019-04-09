package org.col.dao;

import java.util.*;
import java.util.function.Function;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.EntityType;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.*;
import org.col.parser.NameParser;
import org.gbif.nameparser.api.NameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaxonDao {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);
  
  private final SqlSession session;
  private final DescriptionMapper deMapper;
  private final DistributionMapper diMapper;
  private final MediaMapper mMapper;
  private final NameMapper nMapper;
  private final ReferenceMapper rMapper;
  private final SynonymMapper sMapper;
  private final TaxonMapper tMapper;
  private final VernacularNameMapper vMapper;
  private final Map<EntityType, TaxonExtensionMapper<? extends IntKey>> extMapper = new HashMap<>();
  
  
  public TaxonDao(SqlSession sqlSession) {
    this.session = sqlSession;
    deMapper = session.getMapper(DescriptionMapper.class);
    diMapper = session.getMapper(DistributionMapper.class);
    mMapper = session.getMapper(MediaMapper.class);
    nMapper = session.getMapper(NameMapper.class);
    rMapper = session.getMapper(ReferenceMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
    tMapper = session.getMapper(TaxonMapper.class);
    vMapper = session.getMapper(VernacularNameMapper.class);
  
    extMapper.put(EntityType.DISTRIBUTION, diMapper);
    extMapper.put(EntityType.VERNACULAR, vMapper);
    extMapper.put(EntityType.DESCRIPTION, deMapper);
    extMapper.put(EntityType.MEDIA, mMapper);
  }
  
  private String devNull(Reference r) {
    return null;
  }
  
  public DatasetID copyTaxon(final Taxon t, final DatasetID target, ColUser user, Set<EntityType> include) {
    return copyTaxon(t, target.getDatasetKey(), target.getId(), user, include, this::devNull);
  }
  
                             /**
                              * Copies the given source taxon into the dataset and under the parent of targetParent.
                              * The taxon and name source instance will be modified to represent the newly generated taxon and finally persisted.
                              * The original id is retained and finally returned.
                              * An optional set of associated entity types can be indicated to be copied too.
                              *
                              * @return the original source taxon id
                              */
  public DatasetID copyTaxon(final Taxon t, final int targetDatasetKey, final String targetParentID, ColUser user, Set<EntityType> include,
                             Function<Reference,String> lookupReference) {
    final DatasetID orig = new DatasetID(t);
    copyName(t, targetDatasetKey, user, lookupReference);
    
    setKeys(t, targetDatasetKey);
    t.applyUser(user);
    t.setOrigin(Origin.SOURCE);
    t.setParentId(targetParentID);
    tMapper.create(t);
    
    // copy related entities
    for (EntityType type : include) {
      if (extMapper.containsKey(type)) {
        TaxonExtensionMapper<IntKey> mapper = (TaxonExtensionMapper<IntKey>) extMapper.get(type);
        mapper.listByTaxon(orig.getDatasetKey(), orig.getId()).forEach( e -> {
          e.setKey(null);
          ((UserManaged)e).applyUser(user);
          mapper.create( e, t.getId(), targetDatasetKey);
        });
      } else {
        // TODO copy refs, reflinks & name rels
      }
    }
    
    session.commit();
    return orig;
  }
  
  public void copySynonym(final Synonym syn, final DatasetID accepted, ColUser user, Function<Reference,String> lookupReference) {
    copyName(syn, accepted.getDatasetKey(), user, lookupReference);
    newKey(syn);
    syn.applyUser(user);
    syn.setOrigin(Origin.SOURCE);
    sMapper.create(accepted.getDatasetKey(), syn.getName().getId(), accepted.getId(), syn);
  }
  
  /**
   * Copies the given nam instance, modifying the original and assigning a new id
   */
  private void copyName(final NameUsage u, final int targetDatasetKey, ColUser user, Function<Reference,String> lookupReference) {
    Name n = u.getName();
    n.applyUser(user);
    n.setOrigin(Origin.SOURCE);
    if (n.getPublishedInId() != null) {
      Reference ref = setKeys(rMapper.get(n.getDatasetKey(), n.getPublishedInId()), targetDatasetKey);
      //TODO: add sectorKey to reference
      n.setPublishedInId(lookupReference.apply(ref));
    }
    setKeys(n, targetDatasetKey);
    nMapper.create(n);
  }

  private static Taxon setKeys(Taxon t, int datasetKey) {
    t.setDatasetKey(datasetKey);
    return newKey(t);
  }
  
  private static Name setKeys(Name n, int datasetKey) {
    n.setDatasetKey(datasetKey);
    newKey(n);
    //TODO: should we update homotypic name based on the original ids if they are also in the sector???
    n.setHomotypicNameId(n.getId());
    return n;
  }
  
  private static Reference setKeys(Reference r, int datasetKey) {
    r.setDatasetKey(datasetKey);
    return newKey(r);
  }
  
  private static <T extends VerbatimEntity & ID> T newKey(T e) {
    e.setVerbatimKey(null);
    e.setId(UUID.randomUUID().toString());
    return e;
  }
  
  public ResultPage<Taxon> listRoot(Integer datasetKey, Page page) {
    Page p = page == null ? new Page() : page;
    List<Taxon> result = tMapper.listRoot(datasetKey, p);
    int total = result.size() == p.getLimit() ? tMapper.countRoot(datasetKey) : result.size();
    return new ResultPage<>(p, total, result);
  }
  
  public ResultPage<Taxon> list(Integer datasetKey, Page page) {
    Page p = page == null ? new Page() : page;
    List<Taxon> result = tMapper.list(datasetKey, p);
    int total = result.size() == p.getLimit() ? tMapper.count(datasetKey) : result.size();
    return new ResultPage<>(p, total, result);
  }
  
  public Taxon get(int datasetKey, String id) {
    if (id == null) return null;
    return tMapper.get(datasetKey, id);
  }
  
  public List<Synonym> getSynonyms(int datasetKey, String nameId) {
    return sMapper.listByNameID(datasetKey, nameId);
  }

  public Synonym getSynonym(int datasetKey, String nameId) {
    if (nameId != null) {
      List<Synonym> syns = getSynonyms(datasetKey, nameId);
      if (!syns.isEmpty()) {
        if (syns.size() > 1) {
          LOG.debug("Multiple synonyms found for nameID {}", nameId);
        }
        return syns.get(0);
      }
    }
    return null;
  }
  
  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(Taxon taxon) {
    return getSynonymy(taxon.getDatasetKey(), taxon.getId());
  }
  
  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(int datasetKey, String taxonId) {
    Name accName = nMapper.getByTaxon(datasetKey, taxonId);
    Synonymy syn = new Synonymy();
    // get all synonyms and misapplied name
    // they come ordered by status, then homotypic group so its easy to arrange them
    List<Name> group = Lists.newArrayList();
    for (Synonym s : sMapper.listByTaxon(datasetKey, taxonId)) {
      if (TaxonomicStatus.MISAPPLIED == s.getStatus()) {
        syn.addMisapplied(new NameAccordingTo(s.getName(), s.getAccordingTo()));
      } else {
        if (accName.getHomotypicNameId().equals(s.getName().getHomotypicNameId())) {
          syn.getHomotypic().add(s.getName());
        } else {
          if (!group.isEmpty()
              && !group.get(0).getHomotypicNameId().equals(s.getName().getHomotypicNameId())) {
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
    return getClassification(taxon.getDatasetKey(), taxon.getId());
  }
  
  public List<Taxon> getClassification(int datasetKey, String key) {
    return tMapper.classification(datasetKey, key);
  }
  
  public ResultPage<Taxon> getChildren(int datasetKey, String key, Page page) {
    Page p = page == null ? new Page() : page;
    List<Taxon> result = tMapper.children(datasetKey, key, p);
    int total = result.size() == p.getLimit() ?
        tMapper.countChildren(datasetKey, key) : result.size();
    return new ResultPage<>(p, total, result);
  }
  
  public TaxonInfo getTaxonInfo(int datasetKey, String key) {
    return getTaxonInfo(tMapper.get(datasetKey, key));
  }
  
  public TaxonInfo getTaxonInfo(final Taxon taxon) {
    // main taxon object
    if (taxon == null) {
      return null;
    }
    
    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    info.setTaxonReferences(rMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));

    // add all supplementary taxon infos
    info.setDescriptions(deMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setDistributions(diMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setMedia(mMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setVernacularNames(vMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));

    // all reference keys so we can select their details at the end
    Set<String> refIds = new HashSet<>();
    refIds.add(taxon.getName().getPublishedInId());
    refIds.addAll(info.getTaxonReferences());
    info.getDescriptions().forEach(d -> refIds.add(d.getReferenceId()));
    info.getDistributions().forEach(d -> refIds.add(d.getReferenceId()));
    info.getMedia().forEach(m -> refIds.add(m.getReferenceId()));
    info.getVernacularNames().forEach(d -> refIds.add(d.getReferenceId()));
    // make sure we did not add null by accident
    refIds.remove(null);
    
    if (!refIds.isEmpty()) {
      List<Reference> refs = rMapper.listByIds(taxon.getDatasetKey(), refIds);
      info.addReferences(refs);
    }
    
    return info;
  }
  
  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   *
   * @param t
   * @param user
   * @return newly created taxon id
   */
  public String create(Taxon t, ColUser user) {
    final int datasetKey = t.getDatasetKey();
    Name n = t.getName();
    if (n.getId() == null) {
      if (!n.isParsed() && StringUtils.isBlank(n.getScientificName())) {
        throw new IllegalArgumentException("Existing nameId, scientificName or atomized name field required");
      }
      newKey(n);
      n.setOrigin(Origin.USER);
      n.applyUser(user);
      // make sure we use the same dataset
      n.setDatasetKey(datasetKey);
      // does the name need parsing?
      parseName(n);
      nMapper.create(n);
    } else {
      Name nExisting = nMapper.get(datasetKey, n.getId());
      if (nExisting == null) {
        throw new IllegalArgumentException("No name exists with ID " + n.getId() + " in dataset " + datasetKey);
      }
    }
  
    newKey(t);
    t.setOrigin(Origin.USER);
    t.applyUser(user);
    tMapper.create(t);
  
    session.commit();
    
    return t.getId();
  }
  
  private void parseName(Name n){
    if (!n.isParsed()) {
      //TODO: pass in real verbatim record
      VerbatimRecord v = new VerbatimRecord();
      final String authorship = n.getAuthorship();
      NameParser.PARSER.parse(n, v).ifPresent(nat -> {
        // try to add an authorship if not yet there
        NameParser.PARSER.parseAuthorshipIntoName(nat, authorship, v);
      });
      
    } else {
      if (n.getType() == null) {
        n.setType(NameType.SCIENTIFIC);
      }
    }
    n.updateNameCache();
  }
  
  public int update(Taxon t, ColUser user) {
    t.applyUser(user);
  
    final Taxon old = tMapper.get(t.getDatasetKey(), t.getId());
    int res = tMapper.update(t);
    // has parent, i.e. classification been changed ?
    if (!Objects.equals(old.getParentId(), t.getParentId())){
      parentChanged(t.getDatasetKey(), t.getId(), old.getParentId(), t.getParentId());
    }
    session.commit();
    return res;
  }
  
  private void parentChanged(int datasetKey, String id, String oldParentId, String newParentId) {
    // migrate entire DatasetSectors from old to new
    Int2IntMap delta = tMapper.getCounts(datasetKey, id).getCount();
    
    // remove delta
    for (TaxonCountMap tc : tMapper.classificationCounts(datasetKey, oldParentId)) {
      tMapper.updateDatasetSectorCount(Datasets.DRAFT_COL, tc.getId(), mergeMapCounts(tc.getCount(), delta, -1));
    }
    session.commit(true);
    // add counts
    for (TaxonCountMap tc : tMapper.classificationCounts(datasetKey, newParentId)) {
      tMapper.updateDatasetSectorCount(Datasets.DRAFT_COL, tc.getId(), mergeMapCounts(tc.getCount(), delta, 1));
    }
  }
  
  private Int2IntMap mergeMapCounts(Int2IntMap m1, Int2IntMap m2, int factor) {
    for (Int2IntMap.Entry e : m2.int2IntEntrySet()) {
      if (m1.containsKey(e.getIntKey())) {
        m1.put(e.getIntKey(), m1.get(e.getIntKey()) + factor * e.getIntValue());
      } else {
        m1.put(e.getIntKey(), factor * e.getIntValue());
      }
    }
    return m1;
  }

  public void delete(DatasetID obj, ColUser user) {
    Taxon t = tMapper.get(obj.getDatasetKey(), obj.getId());
    relinkChildren(t, user);
    // if this taxon had a sector we need to adjust parental counts
    // we keep the sector as a broken sector around
    SectorMapper sm = session.getMapper(SectorMapper.class);
    Sector s = sm.getByTarget(obj.getDatasetKey(), obj.getId());
    if (s != null) {
      tMapper.incDatasetSectorCount(Datasets.DRAFT_COL, s.getTarget().getId(), s.getDatasetKey(), -1);
    }
    // deleting the taxon should cascade deletes to synonyms, vernaculars, etc
    tMapper.delete(obj.getDatasetKey(), obj.getId());
    session.commit();
  }
  
  public void deleteRecursively(DatasetID obj, ColUser user) {
    throw new NotImplementedException("not yet done");
  }
  
  /**
   * Resets all dataset sector counts for an entire catalogue
   * and rebuilds the counts from the currently mapped sectors
   * @param catalogueKey
   */
  public void updateAllSectorCounts(int catalogueKey, SqlSessionFactory factory) {
    tMapper.resetDatasetSectorCount(Datasets.DRAFT_COL);
    int counter = 0;
    for (Sector s : Pager.sectors(factory)) {
      if (s.getTarget() != null) {
        counter++;
        tMapper.incDatasetSectorCount(catalogueKey, s.getTarget().getId(), s.getDatasetKey(), 1);
      }
    }
    session.commit();
    LOG.info("Updated dataset sector counts from {} sectors", counter);
  }
  
  private void relinkChildren(Taxon t, ColUser user){
    int cnt = tMapper.updateParentId(t.getDatasetKey(), t.getId(), t.getParentId(), user);
    LOG.debug("Moved {} children of {} to {}", cnt, t.getId(), t.getParentId());
  }
  
}
