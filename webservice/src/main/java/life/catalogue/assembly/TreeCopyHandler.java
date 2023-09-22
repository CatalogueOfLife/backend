package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.IgnoreReason;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.CatCopy;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.matching.NameIndex;

import java.util.*;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

public class TreeCopyHandler extends TreeBaseHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeCopyHandler.class);

  private String lastParentID;
  private final List<NameUsageBase> lastUsages = new ArrayList<>();
  private final Map<String, Usage> ids = new HashMap<>();
  private final Map<String, String> nameIds = new HashMap<>();
  private final Map<RanKnName, Usage> implicits = new HashMap<>();

  TreeCopyHandler(int targetDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, User user, Sector sector, SectorImport state) {
    super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state, CatCopy.ID_GENERATOR, CatCopy.ID_GENERATOR, CatCopy.ID_GENERATOR);
  }

  @Override
  public Map<IgnoreReason, Integer> getIgnoredCounter() {
    return ignoredCounter;
  }

  @Override
  public int getDecisionCounter() {
    return decisionCounter;
  }

  /**
   * Copies all name and taxon relations based on ids collected during the accept calls by the tree traversal.
   */
  @Override
  public void copyRelations() {
    // copy name relations
    copyNameRelations();

    // copy taxon relations
    copyTaxonRelations();
  }

  private void copyTaxonRelations() {
    // TODO: copy taxon relations
    LOG.info("Synced {} taxon relations from sector {} - NOT IMPLEMENTED", 0, sector.getKey());
  }

  private void copyNameRelations(){
    // copy name relations
    NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
    NameRelationMapper nrmWrite = batchSession.getMapper(NameRelationMapper.class);
    int counter = 0;
    IntSet relIds = new IntOpenHashSet();

    var key = DSID.of(sector.getSubjectDatasetKey(), "");
    for (Map.Entry<String, String> n : nameIds.entrySet()) {
      for (NameRelation nr : nrm.listByName(key.id(n.getKey()))) {
        if (!relIds.contains((int)nr.getId())) {
          updateFKs(nr);
          nr.setSectorKey(sector.getId());
          nr.setNameId(nameIds.get(nr.getNameId()));
          nr.setRelatedNameId(nameIds.get(nr.getRelatedNameId()));
          if (nr.getNameId() != null && nr.getRelatedNameId() != null) {
            nrmWrite.create(nr);
            relIds.add((int)nr.getId());
            if (counter++ % 2500 == 0) {
              batchSession.commit();
            }
          } else {
            LOG.info("Name relation {} outside of synced sector {}", nr.getKey(), sector.getKey());
          }
        }
      }
    }
    batchSession.commit();
    LOG.info("Synced {} name relations from sector {}", relIds.size(), sector.getKey());
  }

  private void updateFKs(DatasetScopedEntity<?> obj){
    obj.setDatasetKey(sector.getDatasetKey());
    if (obj instanceof VerbatimEntity) {
      ((VerbatimEntity)obj).setVerbatimKey(null);
    }
    if (obj instanceof Referenced) {
      Referenced r = (Referenced) obj;
      r.setReferenceId(lookupReference(r.getReferenceId()));
    }
  }

  @Override
  public void acceptThrows(NameUsageBase u) throws InterruptedException{
    // We buffer all children of a given parentID and sort them by rank before we pass them on to the handler
    if (!Objects.equals(lastParentID, u.getParentId())) {
      processLast();
      lastParentID = u.getParentId();
    }
    lastUsages.add(u);
  }

  private void processLast() throws InterruptedException {
    // sort by rank, then process
    lastUsages.sort(Comparator.comparing(NameUsage::getRank));
    for (var u : lastUsages) {
      process(u);
    }
    lastUsages.clear();
  }

  private void process(NameUsageBase u) throws InterruptedException {
    processCommon(u);

    if (ignoreUsage(u, decisions.get(u.getId()))) {
      // skip this taxon, but include children
      // use taxons parent also as the parentID for this so children link one level up
      ids.put(u.getId(), ids.getOrDefault(u.getParentId(), targetUsage));
      return;
    }

    // all non root nodes have newly created parents
    Usage parent = ids.getOrDefault(u.getParentId(), targetUsage);
    // make sure we have a genus for species and a species for infraspecific taxa
    if (u.isTaxon() && u.getName().getRank().isSpeciesOrBelow()) {
      parent = createImplicit(parent, (Taxon) u);
    }
    String origNameID= u.getName().getId();
    final var orig = DSID.copy(u);
    create(u, parent);

    // remember old to new id mappings
    ids.put(orig.getId(), usage(u));
    nameIds.put(origNameID, u.getName().getId());

    // commit in batches
    if ((sCounter + tCounter) % 1000 == 0) {
      interruptIfCancelled();
      session.commit();
      batchSession.commit();
    }
  }

  @Override
  protected Usage findExisting(Name n, Usage parent) {
    RanKnName rnn = new RanKnName(n.getRank(), n.getScientificName());
    // did we create that implicit name before?
    if (implicits.containsKey(rnn)) {
      return implicits.get(rnn);
    }

    // we need to commit the batch session to see the recent inserts
    batchSession.commit();
    var matches = num.findSimple(targetDatasetKey, sector.getKey().getId(), TaxonomicStatus.ACCEPTED, rnn.rank, rnn.name);
    if (!matches.isEmpty()) {
      var u = new Usage(matches.get(0));
      implicits.put(rnn, u);
      return u;
    }
    return null;
  }

  @Override
  protected void cacheImplicit(Taxon t, Usage parent) {
    implicits.put(new RanKnName(t.getName().getRank(), t.getName().getScientificName()), parent);
  }

  @Override
  public void reset() throws InterruptedException {
    processLast();
    ids.clear();
    super.reset();
  }

  @Override
  public void close() throws InterruptedException {
    processLast();
    super.close();
  }
}
