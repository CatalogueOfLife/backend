package life.catalogue.importer;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.ImporterConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.Create;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
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
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.Validator;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 * Despite its name the PgImporter not only inserts all data from the neo store into postgres,
 * but also indexes the data straight into ES to avoid reading it from Postgres again which is painfully slow
 * because of recursions and large joins.
 *
 * See also https://github.com/CatalogueOfLife/backend/issues/918
 */
public class PgImport implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(PgImport.class);
  
  private final NeoDb store;
  private final int batchSize;
  private final SqlSessionFactory sessionFactory;
  private final DatasetDao datasetDao;
  private final NameUsageIndexService indexService;
  private final int attempt;
  private final DatasetWithSettings dataset;
  private final Validator validator;
  private final Map<Integer, Integer> verbatimKeys = new HashMap<>();
  private LoadingCache<Integer, Set<Issue>> verbatimIssueCache;
  private final Set<String> proParteIds = new HashSet<>();
  private final AtomicInteger nCounter = new AtomicInteger(0);
  private final AtomicInteger tCounter = new AtomicInteger(0);
  private final AtomicInteger sCounter = new AtomicInteger(0);
  private final AtomicInteger bnCounter = new AtomicInteger(0);
  private final AtomicInteger rCounter = new AtomicInteger(0);
  private final AtomicInteger diCounter = new AtomicInteger(0);
  private final AtomicInteger trCounter = new AtomicInteger(0);
  private final AtomicInteger mCounter = new AtomicInteger(0);
  private final AtomicInteger vCounter = new AtomicInteger(0);
  private final AtomicInteger tmCounter = new AtomicInteger(0);
  private final AtomicInteger eCounter = new AtomicInteger(0);
  private int nRelCounter;
  private int tRelCounter;
  private int sRelCounter;
  private int userKey;

  public PgImport(int attempt, DatasetWithSettings dataset, int userKey, NeoDb store,
                  SqlSessionFactory sessionFactory, ImporterConfig cfg, DatasetDao datasetDao, NameUsageIndexService indexService, Validator validator) {
    this.attempt = attempt;
    this.dataset = dataset;
    this.userKey = userKey;
    this.store = store;
    this.batchSize = cfg.batchSize;
    this.sessionFactory = sessionFactory;
    this.indexService = indexService;
    this.datasetDao = datasetDao;
    this.validator = validator;
    verbatimIssueCache = Caffeine.newBuilder()
      .maximumSize(10000)
      .build(key -> store.getVerbatim(key).getIssues());
  }
  
  @Override
  public Boolean call() throws InterruptedException, InterruptedRuntimeException {
    Partitioner.partition(sessionFactory, dataset.getKey(), dataset.getOrigin());
    
    insertVerbatim();
    
    insertReferences();
    
    insertNames();
    
    insertNameRelations();

    insertTypeMaterial();

    insertUsages();

    insertUsageRelations();

    Partitioner.attach(sessionFactory, dataset.getKey(), dataset.getOrigin());
    
    updateMetadata();
		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} synonyms, {} references, " +
        "{} type material, {} vernaculars, {} distributions, {} treatments, {} species estimates, {} media items, " +
        "{} name relations, {} concept relations, {} species interactions",
        dataset.getKey(), verbatimKeys.size(),
        nCounter, tCounter, sCounter, rCounter,
        tmCounter, vCounter, diCounter, trCounter, eCounter, mCounter,
        nRelCounter, tRelCounter, sRelCounter
      );
		return true;
	}

	@VisibleForTesting
  void updateMetadata() {
    try (SqlSession session = sessionFactory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetWithSettings old = new DatasetWithSettings(
        dm.get(dataset.getKey()),
        dm.getSettings(dataset.getKey())
      );
      // archive the previous attempt if existing before we update the current metadata and tie it to a new attempt
      if (old.getDataset().getAttempt() != null) {
        int attempt = old.getDataset().getAttempt();
        DatasetArchiveMapper dam = session.getMapper(DatasetArchiveMapper.class);
        LOG.info("Archive previous dataset metadata with import attempt {} for {}: {}", attempt, dataset.getKey(), dataset.getTitle());
        Dataset archived = dam.get(dataset.getKey(), attempt);
        if (archived == null) {
          // we do not yet have an archived version for given attempt
          dam.create(dataset.getKey());
          var cm = session.getMapper(CitationMapper.class);
          cm.createArchive(dataset.getKey());
        }
      }
      // update current
      if (dataset.isEnabled(Setting.LOCK_METADATA)) {
        LOG.warn("Dataset metadata is locked and won't be updated for {}: {}", dataset.getKey(), dataset.getTitle());

      } else {
        LOG.info("Updating dataset metadata for {}: {}", dataset.getKey(), dataset.getTitle());
        updateMetadata(old.getDataset(), dataset.getDataset(), validator);
        datasetDao.update(old.getDataset(), userKey);
      }

      dm.updateLastImport(dataset.getKey(), attempt);
      LOG.info("Updated last successful import attempt {} for dataset {}: {}", attempt, dataset.getKey(), dataset.getTitle());
    }
  }

  /**
   * Updates the given dataset d with the provided metadata update,
   * retaining managed properties like keys and settings.
   * Mandatory properties like title and license are only changed if not null.
   * @param d
   * @param update
   */
  public static Dataset updateMetadata(Dataset d, Dataset update, Validator validator) {
    Set<String> nonNullProps = Set.of("title", "alias", "license");
    try {
      for (PropertyDescriptor prop : Dataset.PATCH_PROPS) {
        Object val = prop.getReadMethod().invoke(update);
        // for required property do not allow null
        if (val != null || !nonNullProps.contains(prop.getName())) {
          prop.getWriteMethod().invoke(d, val);
        }
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    ObjectUtils.copyIfNotNull(update::getType, d::setType);
    // verify emails, orcids & rorid as they can break validation on insert
    d.processAllAgents(a -> a.validateAndNullify(validator));
    return d;
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
    return updateVerbatimUserEntity(ent, null);
  }

  private <T extends VerbatimEntity & UserManaged & DatasetScoped> T updateVerbatimUserEntity(T ent, Set<Integer> vKeys) {
    ent.setDatasetKey(dataset.getKey());
    return updateUser(updateVerbatimEntity(ent, vKeys));
  }

  private <T extends VerbatimEntity> T updateVerbatimEntity(T ent, @Nullable Set<Integer> vKeys) {
    if (ent != null && ent.getVerbatimKey() != null) {
      if (vKeys != null) {
        vKeys.add(ent.getVerbatimKey());
      }
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
   * Inserts all names, collecting all homotypic name keys for later updates if they haven't been inserted already.
   */
  private void insertNames() {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      final NameMapper nm = session.getMapper(NameMapper.class);
      final NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);

      LOG.debug("Remove existing name matches");
      nmm.deleteByDataset(dataset.getKey());
      session.commit();

      LOG.debug("Inserting all names");
      store.names().all().forEach(n -> {
        n.getName().setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(n.getName());
        updateReferenceKey(n.getName().getPublishedInId(), n.getName()::setPublishedInId);
        nm.create(n.getName());
        if (n.namesIndexId != null) {
          nmm.create(n.getName(), n.getName().getSectorKey(), n.namesIndexId, n.namesIndexMatchType);
        }
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
    nRelCounter = insertRelations(
      RelType::isNameRel,
      NameRelationMapper.class,
      store::toNameRelation
    );
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
   * insert taxa/synonyms with all the rest. Skips bare name usages.
   * This also indexes usages into the ES search index!
   */
  private void insertUsages() throws InterruptedException {
    try (var indexer = indexService.buildDatasetIndexingHandler(dataset.getKey())) {
      final Map<String, List<SimpleDecision>> decisions = new HashMap<>();
      try (SqlSession session = sessionFactory.openSession(true)) {
        AtomicInteger cnt = new AtomicInteger(0);
        session.getMapper(DecisionMapper.class).processDecisions(null, dataset.getKey()).forEach(d -> {
          if (d.getSubject().getId() != null) {
            if (!decisions.containsKey(d.getSubject().getId())) {
              decisions.put(d.getSubject().getId(), new ArrayList<>());
            }
            decisions.get(d.getSubject().getId()).add(new SimpleDecision(d.getId(), d.getDatasetKey(), d.getMode()));
            cnt.incrementAndGet();
          }
        });
        LOG.info("Loaded {} decisions for indexing", cnt);
      }

      try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
        LOG.info("Inserting all taxa & synonyms");
        TreatmentMapper treatmentMapper = session.getMapper(TreatmentMapper.class);
        DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
        EstimateMapper estimateMapper = session.getMapper(EstimateMapper.class);
        MediaMapper mediaMapper = session.getMapper(MediaMapper.class);
        TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
        SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
        VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);

        // iterate over taxonomic tree in depth first order, keeping postgres parent keys
        // pro parte synonyms will be visited multiple times, remember their name ids!
        TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
          Stack<SimpleName> parents = new Stack<>();
          Stack<Node> parentsN = new Stack<>();

          @Override
          public void start(Node n) {
            Set<Integer> vKeys = new HashSet<>();

            NeoUsage u = fillNeoUsage(n, parents.isEmpty() ? null : parents.peek(), vKeys);

            // insert taxon or synonym
            if (u.isSynonym()) {
              if (NeoDbUtils.isProParteSynonym(n)) {
                if (proParteIds.contains(u.getId())) {
                  // we had that id before, append a random suffix for further pro parte usage
                  UUID ppID = UUID.randomUUID();
                  u.setId(u.getId() + "-" + ppID);
                } else {
                  proParteIds.add(u.getId());
                }
              }
              synMapper.create(u.asSynonym());
              sCounter.incrementAndGet();

            } else if (u.isTaxon()){
              taxonMapper.create(updateUser(u.asTaxon()));
              tCounter.incrementAndGet();
              Taxon acc = u.asTaxon();

              // push new postgres key onto stack for this taxon as we traverse in depth first
              // ES indexes only id,rank & name
              parents.push(new SimpleName(acc.getId(), acc.getName().getScientificName(), acc.getName().getRank()));
              parentsN.push(u.node);

              // insert vernacular
              for (VernacularName vn : u.vernacularNames) {
                updateVerbatimUserEntity(vn, vKeys);
                updateReferenceKey(vn);
                vernacularMapper.create(vn, acc.getId());
                vCounter.incrementAndGet();
              }

              // insert distributions
              for (Distribution d : u.distributions) {
                updateVerbatimUserEntity(d, vKeys);
                updateReferenceKey(d);
                distributionMapper.create(d, acc.getId());
                diCounter.incrementAndGet();
              }

              // insert treatments
              if (u.treatment != null) {
                u.treatment.setId(acc.getId());
                updateVerbatimUserEntity(u.treatment, vKeys);
                treatmentMapper.create(u.treatment);
                trCounter.incrementAndGet();
              }

              // insert media
              for (Media m : u.media) {
                updateVerbatimUserEntity(m, vKeys);
                updateReferenceKey(m);
                mediaMapper.create(m, acc.getId());
                mCounter.incrementAndGet();
              }

              // insert estimates
              for (SpeciesEstimate e : u.estimates) {
                updateVerbatimUserEntity(e, vKeys);
                updateReferenceKey(e);
                e.setTarget(SimpleNameLink.of(acc.getId()));
                estimateMapper.create(e);
                eCounter.incrementAndGet();
              }

            } else {
              // a bare name - we only index them into ES
              bnCounter.incrementAndGet();
            }

            // commit in batches
            if ((sCounter.get() + tCounter.get()) % batchSize == 0) {
              interruptIfCancelled();
              session.commit();
              LOG.info("Inserted {} taxa, {} synonyms & {} bare names", tCounter.get(), sCounter.get(), bnCounter.get());
            }

            // index into ES
            NameUsageWrapper nuw = new NameUsageWrapper(u.usage);
            nuw.setPublisherKey(dataset.getGbifPublisherKey());
            nuw.setClassification(new ArrayList<>(parents));
            nuw.setIssues(mergeIssues(vKeys));
            if (u.usage.isSynonym()) {
              NeoUsage acc = fillNeoUsage(parentsN.peek(), parents.peek(), null);
              ((Synonym)u.usage).setAccepted(acc.asTaxon());
              nuw.getClassification().add(new SimpleName(u.usage.getId(), u.usage.getName().getScientificName(), u.usage.getName().getRank()));
            }
            nuw.setDecisions(decisions.get(u.getId()));
            indexer.accept(nuw);
          }

          @Override
          public void end(Node n) {
            interruptIfCancelled();
            // remove this key from parent queue if its an accepted taxon
            if (n.hasLabel(Labels.TAXON)) {
              parents.pop();
              parentsN.pop();
            }
          }
        });
        session.commit();
        LOG.debug("Inserted {} names and {} taxa", nCounter, tCounter);
      }

      // index bare names
      try (Transaction tx = store.getNeo().beginTx()) {
        ResourceIterator<Node> iter = store.bareNames();
        while (iter.hasNext()) {
          Set<Integer> vKeys = new HashSet<>();
          NeoName nn = updateNeoName(iter.next(), vKeys);
          BareName bn = new BareName(nn.getName());
          NameUsageWrapper nuw = new NameUsageWrapper(bn);
          nuw.setPublisherKey(dataset.getGbifPublisherKey());
          nuw.setIssues(mergeIssues(vKeys));
          indexer.accept(nuw);
        }
      }
    }
  }

  private Set<Issue> mergeIssues(Collection<Integer> vKeys){
    Set<Issue> issues = EnumSet.noneOf(Issue.class);
    for (Integer vk : vKeys) {
      if (vk != null) {
        issues.addAll(verbatimIssueCache.get(vk));
      }
    }
    return issues;
  }

  private NeoName updateNeoName(Node n, Set<Integer> vKeys){
    NeoName nn = store.names().objByNode(n);
    updateVerbatimEntity(nn, vKeys);
    nn.getName().setDatasetKey(dataset.getKey());
    updateReferenceKey(nn.getName().getPublishedInId(), nn.getName()::setPublishedInId);
    updateUser(nn.getName());
    return nn;
  }

  private NeoUsage fillNeoUsage(Node n, SimpleName parent, Set<Integer> vKeys){
    NeoUsage u = store.usages().objByNode(n);
    NeoName nn = updateNeoName(store.getUsageNameNode(n), vKeys);

    updateVerbatimEntity(u, vKeys);
    NameUsage nu = u.usage;
    nu.setName(nn.getName());
    nu.setDatasetKey(dataset.getKey());
    if (nu.getAccordingToId() != null) {
      updateReferenceKey(nu.getAccordingToId(), nu::setAccordingToId);
    }
    if (nu instanceof NameUsageBase) {
      NameUsageBase nub = (NameUsageBase)nu;
      updateReferenceKey(nub.getReferenceIds());
      updateUser(nub);
      if (parent != null) {
        nub.setParentId(parent.getId());
      } else if (u.isSynonym()) {
        throw new IllegalStateException("Synonym node " + n.getId() + " without accepted taxon found: " + nn.getName().getScientificName());
      } else if (!n.hasLabel(Labels.ROOT)) {
        throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + nn.getName().getScientificName());
      }
    }
    return u;
  }

  /**
   * Go through all neo4j relations and convert them to taxon concept relations or species interactions if the rel type matches
   */
  private void insertUsageRelations() {
    // taxon concept
    tRelCounter = insertRelations(
      RelType::isTaxonConceptRel,
      TaxonConceptRelationMapper.class,
      store::toConceptRelation
    );

    // species interactions
    sRelCounter = insertRelations(
      RelType::isSpeciesInteraction,
      SpeciesInteractionMapper.class,
      store::toSpeciesInteraction
    );
  }

  private <T extends DatasetScopedEntity<Integer> & Referenced> int insertRelations (
    Predicate<RelType> filter,
    Class<? extends Create<T>> relMapperClass,
    Function<Relationship, T> creator
  ) {
    int total = 0;
    String type = null;
    for (RelType rt : RelType.values()) {
      if (!filter.test(rt)) continue;

      if (type == null && rt.relationClass() != null) {
        type = rt.relationClass().getSimpleName();
      }
      final AtomicInteger counter = new AtomicInteger(0);
      try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
        final Create<T> relMapper = session.getMapper(relMapperClass);
        try (Transaction tx = store.getNeo().beginTx()) {
          store.iterRelations(rt).stream().forEach(rel -> {
            T nr = creator.apply(rel);
            updateReferenceKey(nr);
            relMapper.create(updateUser(nr));
            if (counter.incrementAndGet() % batchSize == 0) {
              interruptIfCancelled();
              session.commit();
            }
          });
        }
        session.commit();
      }
      LOG.debug("Inserted {} {} relations", counter.get(), rt);
      total += counter.get();
    }

    LOG.info("Inserted {} {} relations", total, type);
    return total;
  }


}
