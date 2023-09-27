package life.catalogue.basgroup;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.api.model.Sector;
import life.catalogue.db.mapper.SectorMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple class to map sector priorities for a given dataset.
 * If a usage has no sector, i.e. it is managed in the project, it will have a top priority of -1.
 * Otherwise all priorities must be given explicitly for each sector or result in NULL.
 */
public class SectorPriority {
  private static final Logger LOG = LoggerFactory.getLogger(SectorPriority.class);
  private final Int2IntMap mergeSectorPrios;
  private final int minimum;

  public SectorPriority(int datasetKey, SqlSessionFactory factory) {
    LOG.info("Load sector priorities for dataset {}", datasetKey);
    this.mergeSectorPrios = new Int2IntOpenHashMap();
    int min = 0;
    int max = 0;
    try (SqlSession session = factory.openSession()) {
      var sm = session.getMapper(SectorMapper.class);
      // ordered by priority null sort last so we can increase the maximum for those sectors
      for (var s : sm.listByPriority(datasetKey, Sector.Mode.MERGE)) {
        if (s.getPriority() != null) {
          mergeSectorPrios.put(s.getId(), s.getPriority());
          min = Math.min(min, s.getPriority());
          max = Math.max(max, s.getPriority());
        } else {
          mergeSectorPrios.put((int)s.getId(), ++max);
        }
      }
    }
    this.minimum = min;
    LOG.info("Loaded {} merge sector priorities for dataset {} from {} to {}", mergeSectorPrios.size(), datasetKey, min, max);
  }

  public Integer priority(LinneanNameUsage u) {
    if (u.getSectorKey() == null) {
      // project managed data has highest priority
      return minimum-2;

    } else {
      if (mergeSectorPrios.containsKey((int) u.getSectorKey())) {
        return mergeSectorPrios.get((int) u.getSectorKey());
      }
      // no merge sector, use 2nd highest priority
      return minimum-1;
    }
  }

}
