package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.ImporterConfig;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.mapper.*;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbUtils;
import life.catalogue.importer.neo.model.Labels;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.importer.neo.traverse.StartEndHandler;
import life.catalogue.importer.neo.traverse.TreeWalker;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 *
 */
public class PgImport implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(PgImport.class);
  
  private final NeoDb store;
  private final int batchSize;
  private final SqlSessionFactory sessionFactory;
  private final int attempt;
  private final DatasetWithSettings dataset;
  private final Map<Integer, Integer> verbatimKeys = new HashMap<>();
  private final Set<String> proParteIds = new HashSet<>();
  private final AtomicInteger nCounter = new AtomicInteger(0);
  private final AtomicInteger tCounter = new AtomicInteger(0);
  private final AtomicInteger sCounter = new AtomicInteger(0);
  private final AtomicInteger rCounter = new AtomicInteger(0);
  private final AtomicInteger diCounter = new AtomicInteger(0);
  private final AtomicInteger trCounter = new AtomicInteger(0);
  private final AtomicInteger mCounter = new AtomicInteger(0);
  private final AtomicInteger vCounter = new AtomicInteger(0);
  private final AtomicInteger tmCounter = new AtomicInteger(0);

  public PgImport(int attempt, DatasetWithSettings dataset, NeoDb store, SqlSessionFactory sessionFactory, ImporterConfig cfg) {
    this.attempt = attempt;
    this.dataset = dataset;
    this.store = store;
    this.batchSize = cfg.batchSize;
    this.sessionFactory = sessionFactory;
  }
  
  @Override
  public Boolean call() throws InterruptedException, InterruptedRuntimeException {
    Partitioner.partition(sessionFactory, dataset.getKey());
    
    insertVerbatim();
    
    insertReferences();
    
    insertNames();
    
    insertNameRelations();

    insertTypeMaterial();

    insertUsages();
  
    Partitioner.indexAndAttach(sessionFactory, dataset.getKey());
    
    updateMetadata();
		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} synonyms, {} references, {} vernaculars, {} distributions, {} treatments and {} media items",
        dataset.getKey(), verbatimKeys.size(),
        nCounter, tCounter, sCounter, rCounter, vCounter, diCounter, trCounter, mCounter);
		return true;
	}

  private void updateMetadata() {
    try (SqlSession session = sessionFactory.openSession(false)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetWithSettings old = new DatasetWithSettings(
        dm.get(dataset.getKey()),
        dm.getSettings(dataset.getKey())
      );
      // archive the previous attempt if existing before we update the current metadata and tie it to a new attempt
      if (old.getDataset().getImportAttempt() != null) {
        int attempt = old.getDataset().getImportAttempt();
        DatasetArchiveMapper dam = session.getMapper(DatasetArchiveMapper.class);
        LOG.info("Archive previous dataset metadata with import attempt {} for {}: {}", attempt, dataset.getKey(), dataset.getTitle());
        ArchivedDataset archived = dam.get(dataset.getKey(), attempt);
        if (archived == null) {
          // we do not yet have an archived version for given attempt
          dam.create(dataset.getKey());
        }
      }
      // update current
      LOG.info("Updating dataset metadata for {}: {}", dataset.getKey(), dataset.getTitle());
      updateMetadata(old, dataset);
      dm.updateAll(old);
      dm.updateLastImport(dataset.getKey(), attempt);
      LOG.info("Updated last successful import attempt {} for dataset {}: {}", attempt, dataset.getKey(), dataset.getTitle());
      session.commit();
    }
  }

  /**
   * Updates the given dataset d with the provided metadata update,
   * retaining managed properties like keys and settings
   * @param d
   * @param update
   */
  public static DatasetWithSettings updateMetadata(DatasetWithSettings d, DatasetWithSettings update) {
    copyIfNotNull(update::getAlias, d::setAlias);
    copyIfNotNull(update::getAuthorsAndEditors, d::setAuthorsAndEditors);
    copyIfNotNull(update::getCompleteness, d::setCompleteness);
    copyIfNotNull(update::getConfidence, d::setConfidence);
    copyIfNotNull(update::getContact, d::setContact);
    copyIfNotNull(update::getDescription, d::setDescription);
    copyIfNotNull(update::getGroup, d::setGroup);
    copyIfNotNull(update::getLicense, d::setLicense);
    copyIfNotNull(update::getOrganisations, d::setOrganisations);
    copyIfNotNull(update::getReleased, d::setReleased);
    copyIfNotNull(update::getTitle, d::setTitle);
    copyIfNotNull(update::getType, d::setType);
    copyIfNotNull(update::getVersion, d::setVersion);
    copyIfNotNull(update::getWebsite, d::setWebsite);
    return d;
  }
  
  private static <T> void copyIfNotNull(Supplier<T> getter, Consumer<T> setter) {
    T val = getter.get();
    if (val != null) {
      setter.accept(val);
    }
  }
  
  private void insertVerbatim() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
      int counter = 0;
      Map<Integer, VerbatimRecord> batchCache = new HashMap<>();
      for (VerbatimRecord v : store.verbatimList()) {
        int storeKey = v.getId();
        v.setId(null);
        v.setDatasetKey(dataset.getKey());
        mapper.create(v);
        batchCache.put(storeKey, v);
        if (++counter % batchSize == 0) {
          commitVerbatimBatch(session, batchCache);
          LOG.debug("Inserted {} verbatim records so far", counter);
        }
      }
      commitVerbatimBatch(session, batchCache);
      LOG.info("Inserted {} verbatim records", counter);
    }
  }
  
  private void commitVerbatimBatch(SqlSession session, Map<Integer, VerbatimRecord> batchCache) {
    interruptIfCancelled();
    session.commit();
    // we only get the new keys after we committed in batch mode!!!
    for (Map.Entry<Integer, VerbatimRecord> e : batchCache.entrySet()) {
      verbatimKeys.put(e.getKey(), e.getValue().getId());
    }
    batchCache.clear();
  }
  
  private <T extends VerbatimEntity & UserManaged & DatasetScoped> T updateVerbatimUserEntity(T ent) {
    ent.setDatasetKey(dataset.getKey());
    return updateUser(updateVerbatimEntity(ent));
  }
  
  private <T extends VerbatimEntity> T updateVerbatimEntity(T ent) {
    if (ent != null && ent.getVerbatimKey() != null) {
      ent.setVerbatimKey(verbatimKeys.get(ent.getVerbatimKey()));
    }
    return ent;
  }

  private static <T extends UserManaged> T updateUser(T ent) {
    if (ent != null) {
      ent.setCreatedBy(Users.IMPORTER);
      ent.setModifiedBy(Users.IMPORTER);
    }
    return ent;
  }

  private <T extends Referenced> T updateReferenceKey(T obj){
    if (obj.getReferenceId() != null) {
      obj.setReferenceId(store.references().tmpIdUpdate(obj.getReferenceId()));
    }
    return obj;
  }

  private void updateReferenceKey(String refID, Consumer<String> setter){
    if (refID != null) {
      setter.accept(store.references().tmpIdUpdate(refID));
    }
  }

  private void updateReferenceKey(List<String> refIDs){
    if (refIDs != null) {
      refIDs.replaceAll(r -> store.references().tmpIdUpdate(r));
    }
  }

  private void insertReferences() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      int counter = 0;
      // update all tmp ids to nice ones
      store.references().updateTmpIds();
      for (Reference r : store.references()) {
        r.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(r);
        updateUser(r);
        mapper.create(r);
        rCounter.incrementAndGet();
        if (counter++ % batchSize == 0) {
          interruptIfCancelled();
          session.commit();
          LOG.debug("Inserted {} references", counter);
        }
      }
      session.commit();
      LOG.debug("Inserted all {} references", counter);
    }
  }
  
  
  /**
   * Inserts all names, collecting all homotypic name keys for later updates if they havent been inserted already.
   */
  private void insertNames() {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      final NameMapper nameMapper = session.getMapper(NameMapper.class);
      LOG.debug("Inserting all names");
      store.names().all().forEach(n -> {
        n.name.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(n.name);
        updateReferenceKey(n.name.getPublishedInId(), n.name::setPublishedInId);
        nameMapper.create(n.name);
        if (nCounter.incrementAndGet() % batchSize == 0) {
          interruptIfCancelled();
          session.commit();
          LOG.debug("Inserted {} other names", nCounter.get());
        }
      });
      session.commit();
    }
    LOG.info("Inserted {} name in total", nCounter.get());
  }
  
  /**
   * Go through all neo4j relations and convert them to name acts if the rel type matches
   */
  private void insertNameRelations() {
    for (RelType rt : RelType.values()) {
      if (!rt.isNameRel()) continue;

      final AtomicInteger counter = new AtomicInteger(0);
      try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
        final NameRelationMapper nameRelationMapper = session.getMapper(NameRelationMapper.class);
        LOG.debug("Inserting all {} relations", rt);
        try (Transaction tx = store.getNeo().beginTx()) {
          store.iterRelations(rt).stream().forEach(rel -> {
            NameRelation nr = store.toRelation(rel);
            updateReferenceKey(nr.getPublishedInId(), nr::setPublishedInId);
            nameRelationMapper.create(updateUser(nr));
            if (counter.incrementAndGet() % batchSize == 0) {
              interruptIfCancelled();
              session.commit();
            }
          });
        }
        session.commit();
      }
      LOG.info("Inserted {} {} relations", counter.get(), rt);
    }
  }

  private void insertTypeMaterial() {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      final TypeMaterialMapper tmm = session.getMapper(TypeMaterialMapper.class);
      LOG.debug("Inserting type material");
      // update all tmp ids to nice ones
      store.typeMaterial().updateTmpIds();
      for (TypeMaterial tm : store.typeMaterial()) {
        updateVerbatimUserEntity(tm);
        updateReferenceKey(tm);
        tmm.create(tm);
        if (tmCounter.incrementAndGet() % batchSize == 0) {
          interruptIfCancelled();
          session.commit();
        }
      }
      session.commit();
    }
    LOG.info("Inserted {} type material records", tmCounter);
  }

  /**
   * insert taxa/synonyms with all the rest
   */
  private void insertUsages() throws InterruptedException {
    try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH,false)) {
      LOG.info("Inserting remaining names and all taxa");
      TreatmentMapper treatmentMapper = session.getMapper(TreatmentMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      MediaMapper mediaMapper = session.getMapper(MediaMapper.class);
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
      VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);

      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      // pro parte synonyms will be visited multiple times, remember their name ids!
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<String> parentIds = new Stack<>();
        
        @Override
        public void start(Node n) {
          NeoUsage u = store.usages().objByNode(n);
          NeoName nn = store.nameByUsage(n);
          updateVerbatimEntity(u);
          updateVerbatimEntity(nn);
          // update share props for taxon or synonym
          NameUsageBase nu = u.usage;
          nu.setName(nn.name);
          nu.setDatasetKey(dataset.getKey());
          updateReferenceKey(nu.getAccordingToId(), nu::setAccordingToId);
          updateReferenceKey(nu.getReferenceIds());
          updateUser(nu);
          if (!parentIds.empty()) {
            // use parent postgres key from stack, but keep it there
            nu.setParentId(parentIds.peek());
          } else if (u.isSynonym()) {
            throw new IllegalStateException("Synonym node " + n.getId() + " without accepted taxon found: " + nn.name.getScientificName());
          } else if (!n.hasLabel(Labels.ROOT)) {
            throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + nn.name.getScientificName());
          }
  
          // insert taxon or synonym
          if (u.isSynonym()) {
            if (NeoDbUtils.isProParteSynonym(n)) {
              if (proParteIds.contains(u.getId())){
                // we had that id before, append a random suffix for further pro parte usage
                UUID ppID = UUID.randomUUID();
                u.setId(u.getId() + "-" + ppID);
              } else {
                proParteIds.add(u.getId());
              }
            }
            synMapper.create(u.getSynonym());
            sCounter.incrementAndGet();

          } else {
            taxonMapper.create(updateUser(u.getTaxon()));
            tCounter.incrementAndGet();
            Taxon acc = u.getTaxon();

            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentIds.push(acc.getId());
            
            // insert vernacular
            for (VernacularName vn : u.vernacularNames) {
              updateVerbatimUserEntity(vn);
              updateReferenceKey(vn);
              vernacularMapper.create(vn, acc.getId());
              vCounter.incrementAndGet();
            }
            
            // insert distributions
            for (Distribution d : u.distributions) {
              updateVerbatimUserEntity(d);
              updateReferenceKey(d);
              distributionMapper.create(d, acc.getId());
              diCounter.incrementAndGet();
            }
  
            // insert treatments
            if (u.treatment != null) {
              u.treatment.setId(acc.getId());
              treatmentMapper.create(u.treatment);
              trCounter.incrementAndGet();
            }

            // insert media
            for (Media m : u.media) {
              updateVerbatimUserEntity(m);
              updateReferenceKey(m);
              mediaMapper.create(m, acc.getId());
              mCounter.incrementAndGet();
            }
            
          }

          // commit in batches
          if (counter++ % batchSize == 0) {
            interruptIfCancelled();
            session.commit();
            LOG.info("Inserted {} names and taxa", counter);
          }
        }
        
        @Override
        public void end(Node n) {
          interruptIfCancelled();
          // remove this key from parent queue if its an accepted taxon
          if (n.hasLabel(Labels.TAXON)) {
            parentIds.pop();
          }
        }
      });
      session.commit();
      LOG.debug("Inserted {} names and {} taxa", nCounter, tCounter);
    }
  }
  
}
