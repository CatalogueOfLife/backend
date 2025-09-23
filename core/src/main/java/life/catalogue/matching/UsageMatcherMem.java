package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.cache.UsageCacheSingleDS;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.nidx.NameIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class UsageMatcherMem extends UsageMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(UsageMatcherMem.class);
  private final Int2ObjectMap<List<String>> byCanonNidx = new Int2ObjectOpenHashMap<>();
  private final UsageCacheSingleDS usages = UsageCacheSingleDS.hashMap();

  public UsageMatcherMem(int datasetKey, NameIndex nameIndex) {
    super(datasetKey, nameIndex);
  }

  public int load(SqlSessionFactory factory){
    var cnt = new AtomicInteger();
    LOG.info("Loading usages for dataset {}", datasetKey);
    try (SqlSession session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processDatasetSimpleNidx(datasetKey), sn -> {
        add(sn);
        cnt.incrementAndGet();
      });
    }
    return cnt.intValue();
  }

  @Override
  public void add(SimpleNameCached sn) {
    usages.put(sn);
    if (sn.getCanonicalId() != null) {
      byCanonNidx.computeIfAbsent(sn.getCanonicalId(), k -> new ArrayList<>()).add(sn.getId());
    }
  }

  @Override
  public void updateParent(String oldID, String newID) {
    usages.updateParent(oldID, newID);
  }

  @Override
  public List<SimpleNameCached> getClassification(String usageID) throws NotFoundException {
    return usages.getClassification(usageID);
  }

  @Override
  List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalNidx(int nidx) {
    var canonIDs = byCanonNidx.get(nidx);
    if (canonIDs != null) {
      return canonIDs.stream()
        .map(usages::getSimpleNameClassified)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
}
