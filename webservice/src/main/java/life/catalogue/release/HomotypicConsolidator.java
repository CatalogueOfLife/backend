package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import life.catalogue.api.model.SimpleName;

import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HomotypicConsolidator {
  private final Logger LOG = LoggerFactory.getLogger(getClass());
  private final SqlSessionFactory factory;
  private final int datasetKey;
  private final Int2IntMap sectorPriorities;

  public HomotypicConsolidator(SqlSessionFactory factory, int datasetKey, Int2IntMap sectorPriorities) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    this.sectorPriorities = ObjectUtils.coalesce(sectorPriorities, new Int2IntOpenHashMap());
  }

  public void groupAll() {
    LOG.info("Start homotypic grouping of all names");
    final List<SimpleName> families = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      for (TaxonomicStatus status : TaxonomicStatus.values()) {
        if (status.isTaxon()) {
          families.addAll(num.findSimple(datasetKey, null, status, Rank.FAMILY, null));
        }
      }
    }

    LOG.info("{} accepted families found for homotypic grouping", families.size());
    for (var fam : families) {
      groupFamily(fam);
    }
  }

  public void groupFamily(SimpleName family) {
    LOG.info("Consolidate homotypic groups in family {}", family);
  }
}
