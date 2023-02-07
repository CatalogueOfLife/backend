package life.catalogue.dao;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.io.TempFile;
import life.catalogue.db.PgConnectionRule;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import life.catalogue.db.mapper.DatasetMapper;

import life.catalogue.db.mapper.NameMapper;

import life.catalogue.db.mapper.TaxonMapper;

import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.Rank;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameUsageProcessorMEM {

  @ClassRule
  public static PgConnectionRule pg = new PgConnectionRule("localhost", "colmem", "postgres", "postgres");
  final int datasetKey = 1000;
  final int numSpecies = 1_000_000;

  private TaxonMapper tm;
  private NameMapper nm;

  @Test
  @Ignore
  public void createTestData() {

    try (SqlSession session = pg.getSqlSessionFactory().openSession(false)) {
      var dm = session.getMapper(DatasetMapper.class);
      if (dm.get(datasetKey) == null) {
        Dataset d = TestEntityGenerator.setUserDate(TestEntityGenerator.newDataset("Memory Test"));
        d.setKey(datasetKey);
        d.setOrigin(DatasetOrigin.EXTERNAL);
        dm.create(d);
        System.out.printf("Created dataset %s\n", datasetKey);
      }
    }

    try (SqlSession session = pg.getSqlSessionFactory().openSession(false)) {
      nm = session.getMapper(NameMapper.class);
      tm = session.getMapper(TaxonMapper.class);
      final String kingdom = createTaxon(Rank.KINGDOM, "Plants", null);
      String family = createTaxon(Rank.FAMILY, RandomUtils.randomFamily(), kingdom);
      String genus = createTaxon(Rank.GENUS, RandomUtils.randomGenus(), family);
      for (int i = 1; i <= numSpecies; i++) {
        if (i % 10000 == 0) {
          family = createTaxon(Rank.FAMILY, RandomUtils.randomFamily(), kingdom);
        }
        if (i % 100 == 0) {
          genus = createTaxon(Rank.GENUS, RandomUtils.randomGenus(), family);
        }
        createTaxon(Rank.SPECIES, RandomUtils.randomSpecies(), genus);

        if (i % 1000 == 0) {
          session.commit();
          System.out.printf("Created %s species\n", i);
        }
      }
      session.commit();
    }
  }

  private String createTaxon(Rank rank, String name, String parentID) {
    Name n = TestEntityGenerator.newMinimalName(datasetKey, UUID.randomUUID().toString(), name, rank);
    nm.create(n);
    Taxon t = TestEntityGenerator.newTaxon(n);
    t.setParentId(parentID);
    tm.create(t);
    if (rank != Rank.SPECIES) {
      System.out.printf("Created %s %s\n", rank, t.getId());
    }
    return t.getId();
  }

  public void processDataset() {
    DRH handler = new DRH();
    NameUsageProcessor proc = new NameUsageProcessor(pg.getSqlSessionFactory(), TempFile.directoryFile());
    proc.processDataset(datasetKey, handler);
    assertTrue(handler.taxCounter.get() > numSpecies);
    assertEquals(numSpecies, handler.spCounter.get());
  }
  
  public static class DRH implements Consumer<NameUsageWrapper> {
    public AtomicInteger taxCounter = new AtomicInteger(0);
    public AtomicInteger synCounter = new AtomicInteger(0);
    public AtomicInteger bareCounter = new AtomicInteger(0);
    public AtomicInteger spCounter = new AtomicInteger(0);

    @Override
    public void accept(NameUsageWrapper obj) {
      assertNotNull(obj.getUsage().getId());

      Name n = obj.getUsage().getName();
      assertNotNull(n);
      assertNotNull(n.getId());
      assertNotNull(n.getDatasetKey());
      
      // classification should always include the taxon itself
      // https://github.com/Sp2000/colplus-backend/issues/326
      assertFalse(obj.getClassification().isEmpty());
      SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
      assertEquals(obj.getUsage().getId(), last.getId());
      
      if ( obj.getUsage().isBareName()) {
        bareCounter.incrementAndGet();

        } else {
        if (obj.getUsage().isTaxon()) {
          taxCounter.incrementAndGet();
          if (obj.getUsage().getName().getRank() == Rank.SPECIES) {
            spCounter.incrementAndGet();
        }

        } else if (obj.getUsage().isSynonym()) {
          synCounter.incrementAndGet();
      } else {
          throw new IllegalStateException("Neither bare name, taxon or synonym");
        }
        NameUsageBase nu = (NameUsageBase) obj.getUsage();
        assertNotNull(nu.getId());
        if (nu.getRank() != Rank.KINGDOM) {
          assertNotNull(nu.getParentId());
      }
    }
  }
  }

  public static void main(String[] args) throws Throwable {
    NameUsageProcessorMEM.pg.before();
    try {
      NameUsageProcessorMEM proc = new NameUsageProcessorMEM();
      for (int x=1; x < 10; x++) {
        System.out.printf("\n\n### PROCESS DATASET %s %sth time ###\n", proc.datasetKey, x);
        proc.processDataset();
      }

    } finally {
      NameUsageProcessorMEM.pg.after();
    }
  }
}