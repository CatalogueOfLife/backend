package org.col.dao;

import java.util.*;
import java.util.function.Function;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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

public class TaxonDao extends DatasetEntityDao<Taxon, TaxonMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);
  
  private static final Map<EntityType, Class<? extends TaxonExtensionMapper<? extends GlobalEntity>>> extMapper = new HashMap<>();
  static {
    extMapper.put(EntityType.DISTRIBUTION, DistributionMapper.class);
    extMapper.put(EntityType.VERNACULAR, VernacularNameMapper.class);
    extMapper.put(EntityType.DESCRIPTION, DescriptionMapper.class);
    extMapper.put(EntityType.MEDIA, MediaMapper.class);
  }
  
  public TaxonDao(SqlSessionFactory factory) {
    super(true, factory, TaxonMapper.class);
  }
  
  private static String devNull(Reference r) {
    return null;
  }
  
  private static String devNull(String r) {
    return null;
  }

  public static DatasetID copyTaxon(SqlSession session, final Taxon t, final DatasetID target, int user, Set<EntityType> include) {
    return copyTaxon(session, t, target, user, include, TaxonDao::devNull, TaxonDao::devNull);
  }
  
  /**
   * Copies the given source taxon into the dataset and under the parent of targetParent.
   * The taxon and name source instance will be modified to represent the newly generated taxon and finally persisted.
   * The original id is retained and finally returned.
   * An optional set of associated entity types can be indicated to be copied too.
   *
   * The sectorKey found on the main taxon will also be applied to associated name, reference and other copied entities.
   *
   * @return the original source taxon id
   */
  public static DatasetID copyTaxon(final SqlSession session, final Taxon t, final DatasetID targetParent, int user,
                                    Set<EntityType> include,
                                    Function<Reference, String> lookupReference,
                                    Function<String, String> lookupByIdReference) {
    final DatasetID orig = new DatasetID(t);
    copyName(session, t, targetParent.getDatasetKey(), user, lookupReference);
    
    setKeys(t, targetParent.getDatasetKey());
    t.applyUser(user, true);
    t.setOrigin(Origin.SOURCE);
    t.setParentId(targetParent.getId());
    session.getMapper(TaxonMapper.class).create(t);
    
    // copy related entities
    for (EntityType type : include) {
      if (extMapper.containsKey(type)) {
        final TaxonExtensionMapper<GlobalEntity> mapper = (TaxonExtensionMapper<GlobalEntity>) session.getMapper(extMapper.get(type));
        mapper.listByTaxon(orig.getDatasetKey(), orig.getId()).forEach(e -> {
          e.setKey(null);
          ((UserManaged) e).applyUser(user);
          mapper.create(e, t.getId(), targetParent.getDatasetKey());
        });
        
      } else if (EntityType.REFERENCE == type) {
        // taxon ref links
        final ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
        for (String rid : rm.listByTaxon(orig.getDatasetKey(), orig.getId())) {
          String ridCopy = lookupByIdReference.apply(rid);
          rm.linkToTaxon(t.getDatasetKey(), t.getId(), ridCopy);
        }
  
      } else if (EntityType.NAME_RELATION == type) {
        // TODO copy name rels
      }
    }
    return orig;
  }
  
  /**
   * Copies the given nam instance, modifying the original and assigning a new id
   */
  static void copyName(final SqlSession session, final NameUsageBase u, final int targetDatasetKey, int user,
                               Function<Reference, String> lookupReference) {
    Name n = u.getName();
    n.applyUser(user, true);
    n.setOrigin(Origin.SOURCE);
    if (n.getPublishedInId() != null) {
      ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
      Reference ref = rm.get(n.getDatasetKey(), n.getPublishedInId());
      n.setPublishedInId(lookupReference.apply(ref));
    }
    setKeys(n, targetDatasetKey, u.getSectorKey());
    session.getMapper(NameMapper.class).create(n);
  }
  
  private static Taxon setKeys(Taxon t, int datasetKey) {
    t.setDatasetKey(datasetKey);
    return newKey(t);
  }
  
  private static Name setKeys(Name n, int datasetKey, int sectorKey) {
    n.setDatasetKey(datasetKey);
    n.setSectorKey(sectorKey);
    newKey(n);
    //TODO: should we update homotypic name based on the original ids if they are also in the sector???
    n.setHomotypicNameId(n.getId());
    return n;
  }
  
  private static Reference setKeys(Reference r, int datasetKey, int sectorKey) {
    r.setDatasetKey(datasetKey);
    r.setSectorKey(sectorKey);
    return newKey(r);
  }
  
  public ResultPage<Taxon> listRoot(Integer datasetKey, Page page) {
    try (SqlSession session = factory.openSession(false)) {
      Page p = page == null ? new Page() : page;
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      List<Taxon> result = tm.listRoot(datasetKey, p);
      int total = result.size() == p.getLimit() ? tm.countRoot(datasetKey) : result.size();
      return new ResultPage<>(p, total, result);
    }
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
    try (SqlSession session = factory.openSession(false)) {
      NameMapper nm = session.getMapper(NameMapper.class);
      SynonymMapper sm = session.getMapper(SynonymMapper.class);
      Name accName = nm.getByUsage(datasetKey, taxonId);
      Synonymy syn = new Synonymy();
      // get all synonyms and misapplied name
      // they come ordered by status, then homotypic group so its easy to arrange them
      List<Name> group = Lists.newArrayList();
      for (Synonym s : sm.listByTaxon(datasetKey, taxonId)) {
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
  }
  
  public ResultPage<Taxon> getChildren(int datasetKey, String key, Page page) {
    try (SqlSession session = factory.openSession(false)) {
      Page p = page == null ? new Page() : page;
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      List<Taxon> result = tm.children(datasetKey, key, p);
      int total = result.size() == p.getLimit() ? tm.countChildren(datasetKey, key) : result.size();
      return new ResultPage<>(p, total, result);
    }
  }
  
  public TaxonInfo getTaxonInfo(int datasetKey, String key) {
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      return getTaxonInfo(session, tm.get(datasetKey, key));
    }
  }
  
  public TaxonInfo getTaxonInfo(final Taxon taxon) {
    try (SqlSession session = factory.openSession(false)) {
      return getTaxonInfo(session, taxon);
    }
  }
  
  private TaxonInfo getTaxonInfo(final SqlSession session, final Taxon taxon) {
    // main taxon object
    if (taxon == null) {
      return null;
    }
  
    DistributionMapper dim = session.getMapper(DistributionMapper.class);
    VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
    DescriptionMapper dem = session.getMapper(DescriptionMapper.class);
    MediaMapper mm = session.getMapper(MediaMapper.class);
    ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
  
    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    info.setTaxonReferences(rm.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    
    // add all supplementary taxon infos
    info.setDescriptions(dem.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setDistributions(dim.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setMedia(mm.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setVernacularNames(vm.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    
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
      List<Reference> refs = rm.listByIds(taxon.getDatasetKey(), refIds);
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
  @Override
  public String create(Taxon t, int user) {
    t.setStatusIfNull(TaxonomicStatus.ACCEPTED);
    if (t.getStatus().isSynonym()) {
      throw new IllegalArgumentException("Taxa cannot have a synonym status");
    }

    try (SqlSession session = factory.openSession(false)) {
      final int datasetKey = t.getDatasetKey();
      Name n = t.getName();
      NameMapper nm = session.getMapper(NameMapper.class);
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
        nm.create(n);
      } else {
        Name nExisting = nm.get(datasetKey, n.getId());
        if (nExisting == null) {
          throw new IllegalArgumentException("No name exists with ID " + n.getId() + " in dataset " + datasetKey);
        }
      }
      
      newKey(t);
      t.setOrigin(Origin.USER);
      t.applyUser(user);
      session.getMapper(TaxonMapper.class).create(t);
      
      session.commit();
      
      return t.getId();
    }
  }
  
  static void parseName(Name n) {
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
  
  @Override
  protected void updateAfter(Taxon t, Taxon old, int user, TaxonMapper mapper, SqlSession session) {
    // has parent, i.e. classification been changed ?
    if (!Objects.equals(old.getParentId(), t.getParentId())) {
      parentChanged(mapper, t.getDatasetKey(), t.getId(), old.getParentId(), t.getParentId());
    }
  }
  
  private static void parentChanged(TaxonMapper tm, int datasetKey, String id, String oldParentId, String newParentId) {
    // migrate entire DatasetSectors from old to new
    Int2IntOpenHashMap delta = tm.getCounts(datasetKey, id).getCount();
    if (delta != null && !delta.isEmpty()) {
      // remove delta
      for (TaxonCountMap tc : tm.classificationCounts(datasetKey, oldParentId)) {
        tm.updateDatasetSectorCount(Datasets.DRAFT_COL, tc.getId(), mergeMapCounts(tc.getCount(), delta, -1));
      }
      // add counts
      for (TaxonCountMap tc : tm.classificationCounts(datasetKey, newParentId)) {
        tm.updateDatasetSectorCount(Datasets.DRAFT_COL, tc.getId(), mergeMapCounts(tc.getCount(), delta, 1));
      }
    }
  }
  
  private static Int2IntOpenHashMap mergeMapCounts(Int2IntOpenHashMap m1, Int2IntOpenHashMap m2, int factor) {
    for (Int2IntMap.Entry e : m2.int2IntEntrySet()) {
      if (m1.containsKey(e.getIntKey())) {
        m1.put(e.getIntKey(), m1.get(e.getIntKey()) + factor * e.getIntValue());
      } else {
        m1.put(e.getIntKey(), factor * e.getIntValue());
      }
    }
    return m1;
  }
  
  @Override
  protected void deleteBefore(int datasetKey, String id, Taxon old, int user, TaxonMapper tMapper, SqlSession session) {
    Taxon t = tMapper.get(datasetKey, id);

    int cnt = session.getMapper(NameUsageMapper.class).updateParentId(datasetKey, id, t.getParentId(), user);
    LOG.debug("Moved {} children of {} to {}", cnt, t.getId(), t.getParentId());
    
    if (Datasets.DRAFT_COL == datasetKey) {
      // if this taxon had a sector we need to adjust parental counts
      // we keep the sector as a broken sector around
      SectorMapper sm = session.getMapper(SectorMapper.class);
      for (Sector s : sm.listByTarget(id)) {
        tMapper.incDatasetSectorCount(Datasets.DRAFT_COL, s.getTarget().getId(), s.getDatasetKey(), -1);
      }
    }
    
    // deleting the taxon now should cascade deletes to synonyms, vernaculars, etc but keep the name record!
  }
  
  @Override
  protected void deleteAfter(int datasetKey, String id, Taxon old, int user, TaxonMapper mapper, SqlSession session) {
    //TODO: update ES
  }
  
  /**
   * Does a cascading delete and also deletes all sectors included
   */
  public void deleteRecursively(int datasetKey, String id, ColUser user) {
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      SectorMapper sm = session.getMapper(SectorMapper.class);
  
      // remember sectors and count delte so we can delete them at the end
      List<Integer> sectorKeys = tm.listSectors(datasetKey, id);
      Int2IntOpenHashMap delta = tm.getCounts(datasetKey, id).getCount();
      List<TaxonCountMap> parents = tm.classificationCounts(datasetKey, id);

      // cascading delete
      tm.delete(datasetKey, id);
  
      // remove delta from parents
      for (TaxonCountMap tc : parents) {
        if (!tc.getId().equals(id)) {
          tm.updateDatasetSectorCount(Datasets.DRAFT_COL, tc.getId(), mergeMapCounts(tc.getCount(), delta, -1));
        }
      }
      
      for (Integer key : sectorKeys) {
        sm.delete(key);
      }
    }
    
    //TODO: update ES
  }
  
  /**
   * Resets all dataset sector counts for an entire catalogue
   * and rebuilds the counts from the currently mapped sectors
   *
   * @param catalogueKey
   */
  public void updateAllSectorCounts(int catalogueKey, SqlSessionFactory factory) {
    int counter = 0;
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      tm.resetDatasetSectorCount(Datasets.DRAFT_COL);
      for (Sector s : Pager.sectors(factory)) {
        if (s.getTarget() != null) {
          counter++;
          tm.incDatasetSectorCount(catalogueKey, s.getTarget().getId(), s.getDatasetKey(), 1);
        }
      }
      session.commit();
      
    }
    LOG.info("Updated dataset sector counts from {} sectors", counter);
  }
  
  private void updateClassificationOfDescendants(int datasetKey, String rootId) {
  
  }
}
