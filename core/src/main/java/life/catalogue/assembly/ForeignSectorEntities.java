package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.db.SectorForeignProcessable;
import life.catalogue.db.mapper.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The entities other sectors or editors attached to the usages of a sector, see {@link SectorForeignProcessable}.
 * A sync takes them off before it deletes its usages, as their enforced foreign keys would fail the delete, and attaches
 * them again afterwards to the usages representing the same source usages - a sector sync gives them new ids, a
 * hierarchy sync may resolve them to existing project usages. Every end of a relation that pointed into the sector is
 * rewired, ends outside of it are left alone. Entities whose usage is no longer synced are dropped; a merge sector
 * brings its own back on its next sync. Only ever used against a project, as only a project sync deletes anything.
 * See https://github.com/CatalogueOfLife/backend/issues/1585
 */
class ForeignSectorEntities {
  private static final Logger LOG = LoggerFactory.getLogger(ForeignSectorEntities.class);
  private final SqlSessionFactory factory;
  private final Sector sector;
  // detached entities by their mapper
  private final Map<Class<? extends SectorForeignProcessable<?>>, List<?>> detached = new LinkedHashMap<>();
  // usage of the sector a detached entity was attached to -> the source id it copied, null if it has none
  private final Map<String, String> sourceIds = new HashMap<>();
  // usage of the sector a detached entity was attached to -> the usage representing it now
  private UnaryOperator<String> newIds;
  private int attached;
  private int dropped;

  ForeignSectorEntities(SqlSessionFactory factory, Sector sector) {
    this.factory = factory;
    this.sector = sector;
  }

  /**
   * Takes all foreign entities off the usages of the sector, remembering the source ids of those usages.
   * @return number of detached entities
   */
  int detach() {
    int count = 0;
    // autoCommit=false keeps the cursors alive
    try (SqlSession session = factory.openSession(false)) {
      for (var mc : SectorForeignProcessable.MAPPERS) {
        var mapper = session.getMapper(mc);
        var entities = mapper.listForeignBySector(sector);
        if (!entities.isEmpty()) {
          detached.put(mc, entities);
          mapper.deleteForeignBySector(sector);
          count += entities.size();
          LOG.info("Detached {} {} records other sectors attached to usages of sector {}", entities.size(), entityName(mc), sector.getKey());
        }
      }
      if (count > 0) {
        readSourceIds(session);
      }
      session.commit();
    }
    return count;
  }

  /**
   * Attaches the detached entities to the usages of the sector that copy their source usages now: the new ones after
   * a sync, still the old ones if the sync failed before deleting them.
   */
  void reattach() {
    if (detached.isEmpty()) return;
    final Map<String, String> usageIds;
    try (SqlSession session = factory.openSession(false)) {
      usageIds = readUsageIds(session);
    }
    reattach(usageIds::get);
  }

  /**
   * Attaches the detached entities to the usages that represent their source usages now. Idempotent, every entity is
   * attached at most once: those that cannot be resolved are dropped.
   * @param usageBySourceId resolves a source usage id to the id of the usage representing it now, null if there is none
   */
  void reattach(Function<String, String> usageBySourceId) {
    reattachTo(id -> {
      String sourceId = sourceIds.get(id);
      return sourceId == null ? null : usageBySourceId.apply(sourceId);
    });
  }

  /**
   * Puts the detached entities back where they were, for a sync that failed before it deleted its usages.
   */
  void restore() {
    reattachTo(UnaryOperator.identity());
  }

  private void reattachTo(UnaryOperator<String> newIds) {
    if (detached.isEmpty()) return;
    this.newIds = newIds;
    try (SqlSession session = factory.openSession(false)) {
      for (var e : detached.entrySet()) {
        for (Object entity : e.getValue()) {
          if (attach(session, e.getKey(), entity)) {
            attached++;
          } else {
            dropped++;
          }
        }
      }
      session.commit();
    }
    detached.clear();
    LOG.info("Attached {} entities of other sectors to sector {} again, dropped {}", attached, sector.getKey(), dropped);
  }

  /**
   * @return number of entities that were attached again
   */
  int getAttached() {
    return attached;
  }

  /**
   * @return number of entities that were dropped, as the usage they were attached to is no longer synced,
   *         or a treatment because the usage representing it now already has one
   */
  int getDropped() {
    return dropped;
  }

  @SuppressWarnings("unchecked")
  private boolean attach(SqlSession session, Class<? extends SectorForeignProcessable<?>> mc, Object entity) {
    switch (entity) {
      case TaxonExtension<?> te -> {
        String taxonId = remap(te.getTaxonID());
        if (taxonId == null) return false;
        ((TaxonExtensionMapper<ExtensionEntity>) session.getMapper(mc)).create(te.getObj(), taxonId);
      }
      case Treatment t -> {
        // a treatment's own id is the taxon id
        String taxonId = remap(t.getId());
        if (taxonId == null) return false;
        // a hierarchy sync can resolve to an existing project usage with its own treatment
        var tm = session.getMapper(TreatmentMapper.class);
        if (tm.get(DSID.of(sector.getDatasetKey(), taxonId)) != null) return false;
        t.setId(taxonId);
        tm.create(t);
      }
      case SpeciesInteraction si -> {
        if (lost(si.getTaxonId()) || lost(si.getRelatedTaxonId())) return false;
        si.setTaxonId(remap(si.getTaxonId()));
        si.setRelatedTaxonId(remap(si.getRelatedTaxonId()));
        session.getMapper(SpeciesInteractionMapper.class).create(si);
      }
      case TaxonConceptRelation tc -> {
        if (lost(tc.getTaxonId()) || lost(tc.getRelatedTaxonId())) return false;
        tc.setTaxonId(remap(tc.getTaxonId()));
        tc.setRelatedTaxonId(remap(tc.getRelatedTaxonId()));
        session.getMapper(TaxonConceptRelationMapper.class).create(tc);
      }
      default -> throw new IllegalStateException("Unsupported foreign entity " + entity.getClass().getSimpleName());
    }
    return true;
  }

  /**
   * @return the id of the usage that represents the source usage of a former sector usage now, null if there is none.
   *         Ids outside the sector stay as they are.
   */
  private String remap(String id) {
    if (id == null || !sourceIds.containsKey(id)) return id;
    return newIds.apply(id);
  }

  /**
   * @return true if the id was a usage of the sector whose source usage is no longer synced
   */
  private boolean lost(String id) {
    return id != null && sourceIds.containsKey(id) && remap(id) == null;
  }

  private void readSourceIds(SqlSession session) {
    // all ends of the detached entities, only those in the sector are found below
    Set<String> ids = new HashSet<>();
    for (var entities : detached.values()) {
      for (Object entity : entities) {
        switch (entity) {
          case TaxonExtension<?> te -> ids.add(te.getTaxonID());
          case Treatment t -> ids.add(t.getId());
          case SpeciesInteraction si -> { ids.add(si.getTaxonId()); ids.add(si.getRelatedTaxonId()); }
          case TaxonConceptRelation tc -> { ids.add(tc.getTaxonId()); ids.add(tc.getRelatedTaxonId()); }
          default -> throw new IllegalStateException("Unsupported foreign entity " + entity.getClass().getSimpleName());
        }
      }
    }
    final Map<Integer, List<String>> idsByVSKey = new HashMap<>();
    try {
      try (var cursor = session.getMapper(NameUsageMapper.class).processSectorSimple(sector)) {
        for (SimpleNameVerbatim u : cursor) {
          if (ids.contains(u.getId())) {
            sourceIds.put(u.getId(), null);
            if (u.getVerbatimSourceKey() != null) {
              idsByVSKey.computeIfAbsent(u.getVerbatimSourceKey(), k -> new ArrayList<>()).add(u.getId());
            }
          }
        }
      }
      try (var cursor = session.getMapper(VerbatimSourceMapper.class).processSector(sector)) {
        for (VerbatimSource v : cursor) {
          var usages = idsByVSKey.get(v.getId());
          if (usages != null) {
            usages.forEach(id -> sourceIds.put(id, v.getSourceId()));
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read the usages of sector " + sector.getKey(), e);
    }
  }

  /**
   * @return source id -> usage of the sector that copies it now
   */
  private Map<String, String> readUsageIds(SqlSession session) {
    final Map<String, String> usageIds = new HashMap<>();
    final Set<String> wanted = new HashSet<>(sourceIds.values());
    wanted.remove(null);
    final Map<Integer, String> sourceIdsByVSKey = new HashMap<>();
    try {
      try (var cursor = session.getMapper(VerbatimSourceMapper.class).processSector(sector)) {
        for (VerbatimSource v : cursor) {
          if (v.getSourceId() != null && wanted.contains(v.getSourceId())) {
            sourceIdsByVSKey.put(v.getId(), v.getSourceId());
          }
        }
      }
      // a taxon wins over a synonym copying the same source usage
      Set<String> taxa = new HashSet<>();
      try (var cursor = session.getMapper(NameUsageMapper.class).processSectorSimple(sector)) {
        for (SimpleNameVerbatim u : cursor) {
          String sourceId = u.getVerbatimSourceKey() == null ? null : sourceIdsByVSKey.get(u.getVerbatimSourceKey());
          if (sourceId != null) {
            boolean taxon = u.getStatus() != null && u.getStatus().isTaxon();
            if (taxon && taxa.add(sourceId)) {
              usageIds.put(sourceId, u.getId());
            } else if (!taxa.contains(sourceId)) {
              usageIds.putIfAbsent(sourceId, u.getId());
            }
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read the usages of sector " + sector.getKey(), e);
    }
    return usageIds;
  }

  private static String entityName(Class<?> mapperClass) {
    return mapperClass.getSimpleName().replaceAll("Mapper", "");
  }
}
