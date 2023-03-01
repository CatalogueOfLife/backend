package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.TestDataRule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.cursor.Cursor;
import org.junit.Assert;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class NameUsageWrapperMapperTreeTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTreeTest() {
    super(NameUsageWrapperMapper.class, TestDataRule.tree());
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void getTaxa() throws Exception {
    
    List<?> cl = mapper(TaxonMapper.class).classificationSimple(DSID.of(NAME4.getDatasetKey(), "t15"));
    assertEquals(7, cl.size());
    
    NameUsageWrapper tax = mapper().get(NAME4.getDatasetKey(), "t15");
    assertFalse(tax.getClassification().isEmpty());
    assertEquals(cl, tax.getClassification());
    
    // now add decisions!!
    DecisionMapper dm = mapper(DecisionMapper.class);
    EditorialDecision ed1 = TestEntityGenerator.newDecision(Datasets.COL, NAME4.getDatasetKey(), "t15");
    dm.create(ed1);
    // broken
    EditorialDecision ed2 = TestEntityGenerator.newDecision(Datasets.COL, NAME4.getDatasetKey(), "t1556");
    dm.create(ed2);
    EditorialDecision ed3 = TestEntityGenerator.newDecision(12, NAME4.getDatasetKey(), "t15");
    dm.create(ed3);
    commit();
    
    tax = mapper().get(NAME4.getDatasetKey(), "t15");
    assertFalse(tax.getClassification().isEmpty());
    assertEquals(cl, tax.getClassification());
    assertEquals(2, tax.getDecisions().size());
    Set<DSID<Integer>> keys = new HashSet<>();
    for (SimpleDecision sd : tax.getDecisions()) {
      keys.add(sd.getKey());
      if (sd.getKey().equals(DSID.copy(ed1.getKey()))) {
        assertEquals(ed1.asSimpleDecision(), sd);
        
      } else if (sd.getKey().equals(DSID.copy(ed2.getKey()))) {
        fail("broken decision");
  
      } else if (sd.getKey().equals(DSID.copy(ed3.getKey()))) {
        assertEquals(ed3.asSimpleDecision(), sd);

      } else {
        fail("Unknown decision");
      }
    }
    assertEquals(2, keys.size());
  }

  @Test
  public void processDatasetBareNames() throws Exception {
    try (Cursor<NameUsageWrapper> c = mapper().processDatasetBareNames(NAME4.getDatasetKey(), null)) {
      c.forEach(obj -> {
          counter.incrementAndGet();
          assertNotNull(obj);
          assertNotNull(obj.getUsage());
          assertNotNull(obj.getUsage().getName());
      });
      Assert.assertEquals(0, counter.get());
    }
  }
  
  @Test
  public void processSubtree() throws Exception {
    try (Cursor<SimpleNameClassification> c = mapper().processTree(NAME4.getDatasetKey(), null, "t4")) {
      c.forEach(obj -> {
          counter.incrementAndGet();
          assertNotNull(obj.getClassification());

          // classification should always include the taxon itself
          // https://github.com/Sp2000/colplus-backend/issues/326
          assertFalse(obj.getClassification().isEmpty());
          SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
          assertEquals(obj.getId(), last.getId());

          // classification should always start with the root of the dataset, not the root of the traversal!
          assertEquals("t1", obj.getClassification().get(0).getId());
          assertEquals("t2", obj.getClassification().get(1).getId());
          assertEquals("t3", obj.getClassification().get(2).getId());
      });
      Assert.assertEquals(21, counter.get());
    }
  }

  @Test
  public void processWithoutClassification() throws Exception {
    final int datasetKey = NAME4.getDatasetKey();

    // create multiple decisions for a usage to make sure they get aggregated
    DecisionMapper dm = mapper(DecisionMapper.class);
    DatasetMapper dam = mapper(DatasetMapper.class);
    final int numProjects = 6;
    for (int x = 0; x<numProjects; x++) {
      // also create a new project to not interfer with the same decision
      Dataset d = TestEntityGenerator.newDataset("project "+x);
      d.setOrigin(DatasetOrigin.PROJECT);
      d.setKey(10000+x);
      d.applyUser(TestEntityGenerator.USER_USER);
      dam.create(d);
      Partitioner.partition(session(), d.getKey(), DatasetOrigin.PROJECT);

      dm.create(TestEntityGenerator.newDecision(d.getKey(), datasetKey, "t15")); // taxon
      dm.create(TestEntityGenerator.newDecision(d.getKey(), datasetKey, "s22")); // synonym
      dm.create(TestEntityGenerator.newDecision(d.getKey(), datasetKey, "t20")); // synonyms accepted
    }
    commit();

    // build temporary table collecting issues from all usage related tables
    // we do this in a separate step to not overload postgres with gigantic joins later on
    mapper(VerbatimRecordMapper.class).createTmpIssuesTable(datasetKey, null);

    final Set<String> ids = new HashSet<>();
    try (var c = mapper().processWithoutClassification(datasetKey, null)) {
      c.forEach(obj -> {
        System.out.println(obj);
        counter.incrementAndGet();
        assertFalse(ids.contains(obj.getId()));
        ids.add(obj.getId());
        assertTrue(obj.getClassification() == null || obj.getClassification().isEmpty());
        switch (obj.getId()) {
          case "t15":
          case "t20":
          case "s22":
            assertEquals(numProjects, obj.getDecisions().size());
            break;
        }
      });
      Assert.assertEquals(24, counter.get());
    }
  }

  @Test
  public void processDev() throws Exception {
    final int datasetKey = 1049;

    // build temporary table collecting issues from all usage related tables
    // we do this in a separate step to not overload postgres with gigantic joins later on
    mapper(VerbatimRecordMapper.class).createTmpIssuesTable(datasetKey, null);

    final Set<String> ids = new HashSet<>();
    AtomicInteger synCounter = new AtomicInteger(0);
    try (var c = mapper().processWithoutClassification(datasetKey, null)) {
      c.forEach(obj -> {
        System.out.println(obj);
        counter.incrementAndGet();
        if (obj.getUsage().isSynonym()) {
          synCounter.incrementAndGet();
        }
        assertFalse(ids.contains(obj.getId()));
        ids.add(obj.getId());
        switch (obj.getId()) {
          case "120083805":
          case "120082457":
            System.out.println("WATCH OUT FOR DUPE!");
            break;
        }
      });
      System.out.println("Finished. Total="+counter.get() + ", synonyms="+synCounter.get());
    }
  }
}
