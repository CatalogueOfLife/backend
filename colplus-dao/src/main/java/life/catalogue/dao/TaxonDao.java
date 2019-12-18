package life.catalogue.dao;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.*;
import life.catalogue.parser.NameParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class TaxonDao extends DatasetEntityDao<String, Taxon, TaxonMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);
  
  public TaxonDao(SqlSessionFactory factory) {
    super(true, factory, TaxonMapper.class);
  }
  
  public static DSID<String> copyTaxon(SqlSession session, final Taxon t, final DSID<String> target, int user, Set<EntityType> include) {
    return CatCopy.copyUsage(session, t, target, user, include, TaxonDao::devNull, TaxonDao::devNull);
  }
  
  public ResultPage<Taxon> listRoot(Integer datasetKey, Page page) {
    try (SqlSession session = factory.openSession(false)) {
      Page p = page == null ? new Page() : page;
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      List<Taxon> result = tm.listRoot(datasetKey, p);
      return new ResultPage<>(p, result, () -> tm.countRoot(datasetKey));
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
  
  public ResultPage<Taxon> getChildren(final DSID<String> key, Page page) {
    try (SqlSession session = factory.openSession(false)) {
      Page p = page == null ? new Page() : page;
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      List<Taxon> result = tm.children(key, p);
      return new ResultPage<>(p, result, () -> tm.countChildren(key));
    }
  }
  
  public TaxonInfo getTaxonInfo(int datasetKey, String id) {
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      return getTaxonInfo(session, tm.get(new DSIDValue(datasetKey, id)));
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
  
    SynonymMapper sm = session.getMapper(SynonymMapper.class);
    DistributionMapper dim = session.getMapper(DistributionMapper.class);
    VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
    DescriptionMapper dem = session.getMapper(DescriptionMapper.class);
    MediaMapper mm = session.getMapper(MediaMapper.class);
    ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
    TypeMaterialMapper tmm = session.getMapper(TypeMaterialMapper.class);

    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    // all reference keys so we can select their details at the end
    Set<String> refIds = new HashSet<>(taxon.getReferenceIds());
    refIds.add(taxon.getName().getPublishedInId());

    // synonyms
    info.setSynonyms(sm.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.getSynonyms().forEach(s -> refIds.addAll(s.getReferenceIds()));

    // add all supplementary taxon infos
    info.setDescriptions(dem.listByTaxon(taxon));
    info.getDescriptions().forEach(d -> refIds.add(d.getReferenceId()));
    
    info.setDistributions(dim.listByTaxon(taxon));
    info.getDistributions().forEach(d -> refIds.add(d.getReferenceId()));
    
    info.setMedia(mm.listByTaxon(taxon));
    info.getMedia().forEach(m -> refIds.add(m.getReferenceId()));
    
    info.setVernacularNames(vm.listByTaxon(taxon));
    info.getVernacularNames().forEach(d -> refIds.add(d.getReferenceId()));

    // add all type material
    info.getTypeMaterial().put(taxon.getName().getId(), tmm.listByName(taxon.getName()));
    info.getSynonyms().forEach(s -> info.getTypeMaterial().put(s.getName().getId(), tmm.listByName(s.getName())));
    info.getTypeMaterial().values().forEach(
            types -> types.forEach(
                    t -> refIds.add(t.getReferenceId())
            )
    );

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
  public DSID<String> create(Taxon t, int user) {
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
        Name nExisting = nm.get(DSID.key(datasetKey, n.getId()));
        if (nExisting == null) {
          throw new IllegalArgumentException("No name exists with ID " + n.getId() + " in dataset " + datasetKey);
        }
      }
      
      newKey(t);
      t.setOrigin(Origin.USER);
      t.applyUser(user);
      session.getMapper(TaxonMapper.class).create(t);
      
      session.commit();
      
      return t;
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
      parentChanged(mapper, t, old.getParentId(), t.getParentId());
    }
  }
  
  private static void parentChanged(TaxonMapper tm, final DSID<String> key, String oldParentId, String newParentId) {
    // migrate entire DatasetSectors from old to new
    Int2IntOpenHashMap delta = tm.getCounts(key).getCount();
    if (delta != null && !delta.isEmpty()) {
      DSID<String> parentKey =  DSID.key(key.getDatasetKey(), oldParentId);
      // reusable catalogue key instance
      final DSIDValue<String> catKey = DSID.key(key.getDatasetKey(), "");
      // remove delta
      for (TaxonCountMap tc : tm.classificationCounts(parentKey)) {
        tm.updateDatasetSectorCount(catKey.id(tc.getId()), mergeMapCounts(tc.getCount(), delta, -1));
      }
      // add counts
      parentKey.setId(newParentId);
      for (TaxonCountMap tc : tm.classificationCounts(parentKey)) {
        tm.updateDatasetSectorCount(catKey.id(tc.getId()), mergeMapCounts(tc.getCount(), delta, 1));
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
  protected void deleteBefore(DSID<String> did, Taxon old, int user, TaxonMapper tMapper, SqlSession session) {
    Taxon t = tMapper.get(did);

    int cnt = session.getMapper(NameUsageMapper.class).updateParentId(did.getDatasetKey(), did.getId(), t.getParentId(), user);
    LOG.debug("Moved {} children of {} to {}", cnt, t.getId(), t.getParentId());
    
    // if this taxon had a sector we need to adjust parental counts
    // we keep the sector as a broken sector around
    SectorMapper sm = session.getMapper(SectorMapper.class);
    for (Sector s : sm.listByTarget(did.getDatasetKey(), did.getId())) {
      tMapper.incDatasetSectorCount(s.getTargetAsDSID(), s.getSubjectDatasetKey(), -1);
    }
    // deleting the taxon now should cascade deletes to synonyms, vernaculars, etc but keep the name record!
  }
  
  @Override
  protected void deleteAfter(DSID<String> did, Taxon old, int user, TaxonMapper mapper, SqlSession session) {
    //TODO: update ES
  }
  
  /**
   * Does a cascading delete and also deletes all sectors included
   */
  public void deleteRecursively(DSID<String> id, ColUser user) {
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      SectorMapper sm = session.getMapper(SectorMapper.class);
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
  
      // remember sectors and counts so we can delete them at the end
      Int2IntOpenHashMap delta = tm.getCounts(id).getCount();
      LOG.debug("Delete taxon {} and its {} nested sectors from dataset {} by user {}", id, delta.size(), id.getDatasetKey(), user);
      List<TaxonCountMap> parents = tm.classificationCounts(id);

      // cascading delete removes descendants and vernacular, distributions, descriptions, media
      // but NOT names, name_rels or refs
      tm.delete(id);
      // TODO: remove orphaned names and references ?
  
      // remove delta from parents
      DSIDValue<String> key = DSID.copy(id);
      for (TaxonCountMap tc : parents) {
        if (!tc.getId().equals(id.getId())) {
          tm.updateDatasetSectorCount(key.id(tc.getId()), mergeMapCounts(tc.getCount(), delta, -1));
        }
      }
      
      for (int skey : delta.keySet()) {
        LOG.debug("Delete sector {} and its imports by user {}", skey, user);
        sim.delete(skey);
        sm.delete(skey);
      }
      session.commit();
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
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      SectorMapper sm = session.getMapper(SectorMapper.class);
      tm.resetDatasetSectorCount(catalogueKey);
      SectorCountUpdHandler scConsumer = new SectorCountUpdHandler(tm);
      sm.processDataset(catalogueKey).forEach(scConsumer);
      session.commit();
      LOG.info("Updated dataset sector counts from {} sectors", scConsumer.counter);
    }
  }
  
  static class SectorCountUpdHandler implements Consumer<Sector> {
    private final TaxonMapper tm;
    int counter = 0;
  
    SectorCountUpdHandler(TaxonMapper tm) {
      this.tm = tm;
    }
  
    @Override
    public void accept(Sector s) {
      if (s.getTarget() != null) {
        counter++;
        tm.incDatasetSectorCount(s.getTargetAsDSID(), s.getSubjectDatasetKey(), 1);
      }
    }
  }
  
  private static String devNull(Reference r) {
    return null;
  }
  
  private static String devNull(String r) {
    return null;
  }
  
}
