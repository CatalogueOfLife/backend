package org.col.admin.assembly;

import java.util.Queue;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Decision;
import org.col.api.model.Sector;
import org.col.db.mapper.SectorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContinuousAssembly {
  private static final Logger LOG = LoggerFactory.getLogger(ContinuousAssembly.class);
  
  private final SqlSessionFactory factory;
  private final Queue<Decision> queue;
  
  public ContinuousAssembly(SqlSessionFactory factory, Queue<Decision> queue) {
    this.factory = factory;
    this.queue = queue;
  }
  
  
  public Object getState() {
    return "Queue size: " + queue.size();
  }
  
  public void syncSector(int sectorKey) {
    try (SqlSession session = factory.openSession()) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s  = sm.get(sectorKey);
      
    }
  }
}
