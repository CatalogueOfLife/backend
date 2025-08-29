package life.catalogue.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;

import life.catalogue.db.mapper.SectorMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SectorInfoCache {
  protected final Int2ObjectMap<SectorInfo> sector2info = new Int2ObjectOpenHashMap<>();
  private final SectorInfo emptyInfo = new SectorInfo(new Sector());
  private final SqlSessionFactory factory;
  private final int datasetKey;

  public SectorInfoCache(SqlSessionFactory factory, int datasetKey) {
    this.factory = factory;
    this.datasetKey = datasetKey;
  }

  /**
   * @return all subjectDatasetKeys from the so far loaded sectors
   */
  public Set<Integer> sourceKeys() {
    return sector2info.values().stream()
      .map(s -> s.sourceDatasetKey)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public int size() {
    return sector2info.size();
  }

  private static class SectorInfo {
    final Integer sourceDatasetKey;
    final Sector.Mode mode;

    public SectorInfo(Sector s) {
      mode = s.getMode();
      sourceDatasetKey = s.getSubjectDatasetKey();
    }
  }

  private SectorInfo sector2info(Integer sectorKey) {
    if (sectorKey != null) {
      int sk = sectorKey;
      if (!sector2info.containsKey(sk)) {
        try (SqlSession session = factory.openSession()) {
          Sector s = session.getMapper(SectorMapper.class).get(DSID.of(datasetKey, sectorKey));
          sector2info.put(sk, s==null ? emptyInfo : new SectorInfo(s));
        }
      }
      return sector2info.get(sk);
    }
    return emptyInfo;
  }

  public Integer sector2datasetKey(Integer sectorKey){
    return sector2info(sectorKey).sourceDatasetKey;
  }

  public Sector.Mode sector2mode(Integer sectorKey){
    return sector2info(sectorKey).mode;
  }
}
