package life.catalogue.basgroup;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import life.catalogue.api.model.Sector;
import life.catalogue.db.mapper.SectorMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorPriority {
  private static final Logger LOG = LoggerFactory.getLogger(SectorPriority.class);
  private final Int2IntMap sectorPriorities;

  public SectorPriority(Int2IntMap sectorPriorities) {
    this.sectorPriorities = sectorPriorities;
  }

  SectorPriority(int datasetKey, SqlSessionFactory factory) {
    LOG.info("Load sector priorities for dataset {}", datasetKey);
    this.sectorPriorities = new Int2IntOpenHashMap();
    try (SqlSession session = factory.openSession()) {
      var sm = session.getMapper(SectorMapper.class);
      for (var s : sm.listByPriority(datasetKey, Sector.Mode.MERGE)) {
        if (s.getPriority() != null) {
          sectorPriorities.put(s.getId(), s.getPriority());
        }
      }
    }
  }

  public Integer priority(LinneanNameUsage u) {
    if (u.getSectorKey() != null && sectorPriorities.containsKey((int) u.getSectorKey())) {
      return sectorPriorities.get((int) u.getSectorKey());
    }
    return null;
  }

}
