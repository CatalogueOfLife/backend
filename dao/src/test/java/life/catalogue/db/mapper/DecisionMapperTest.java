package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DataEntity;
import life.catalogue.api.model.DatasetScoped;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.io.Resources;
import life.catalogue.junit.PgSetupRule;

import org.gbif.nameparser.api.Rank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;

public class DecisionMapperTest extends BaseDecisionMapperTest<EditorialDecision, DecisionSearchRequest, DecisionMapper> {
  
  public DecisionMapperTest() {
    super(DecisionMapper.class);
  }
  
  final int catalogeKey = Datasets.COL;
  final int subjectDatasetKey = DATASET11.getKey();
  
  @Test
  public void brokenDecisions() {
    EditorialDecision d1 = createTestEntity(catalogeKey);
    d1.getSubject().setId(TestEntityGenerator.TAXON1.getId());
    mapper().create(d1);

    EditorialDecision d2 = createTestEntity(catalogeKey);
    mapper().create(d2);
    commit();
  
    DecisionSearchRequest req = DecisionSearchRequest.byProject(catalogeKey);
    assertEquals(2, mapper().search(req,null).size());
    
    req.setSubjectDatasetKey(subjectDatasetKey);
    assertEquals(2, mapper().search(req,null).size());
    
    req.setId(TestEntityGenerator.TAXON1.getId());
    assertEquals(1, mapper().search(req,null).size());
  
    req = DecisionSearchRequest.byDataset(catalogeKey, subjectDatasetKey);
    req.setBroken(true);
    assertEquals(1, mapper().search(req,null).size());
  
    req = DecisionSearchRequest.byProject(catalogeKey);
    req.setModifiedBy(d1.getCreatedBy());
    assertEquals(2, mapper().search(req,null).size());
  
    req.setModifiedBy(999);
    assertEquals(0, mapper().search(req,null).size());

    req = DecisionSearchRequest.byProject(catalogeKey);
    req.setName("Harakiri");
    assertEquals(0, mapper().search(req,null).size());

    req.setName(d2.getSubject().getName().substring(0, 4).toUpperCase());
    List<EditorialDecision> res = mapper().search(req,null);
    assertEquals(1, res.size());
    assertEquals(d2.getId(), res.get(0).getId());

    req.setMode(EditorialDecision.Mode.REVIEWED);
    assertEquals(0, mapper().search(req,null).size());
  }

  @Test
  public void testTransactionPerformance() {
    EditorialDecision d = createTestEntity(catalogeKey);
    commit();

    StopWatch watch = StopWatch.createStarted();
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(false)) {
      var dm = session.getMapper(DecisionMapper.class);
      for (int i = 0; i < 1000; i++) {
        d.getSubject().setId(String.valueOf(i*2));
        dm.create(d);
      }
      session.commit();
    }
    watch.stop();
    System.out.println("CREATED !!!");
    System.out.println(watch);

    int success = 0;
    int existed = 0;
    int failed = 0;
    watch = StopWatch.createStarted();
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(false)) {
      var dm = session.getMapper(DecisionMapper.class);
      for (int i = 0; i < 2000; i++) {
        d.getSubject().setId(String.valueOf(i));
        d.setId(i);
        try {
          if (dm.existsWithKeyOrSubject(d)) {
            existed++;

          } else {
            dm.createWithID(d);
            success++;
          }
        } catch (PersistenceException e) {
          failed++;
          System.out.println(e);
        }
      }
      session.commit();
    }
    watch.stop();
    System.out.println("CREATED AGAIN !!!");
    System.out.println(watch);
    System.out.println("Success: "+success);
    System.out.println("Existed: "+existed);
    System.out.println("Failed: "+failed);
  }

  @Override
  void updateTestObj(EditorialDecision ed) {
    ed.setNote("My next note");
    ed.setName(TestEntityGenerator.newName("updatedID"));
  }
  
  @Override
  EditorialDecision createTestEntity(int dkey) {
    return create(subjectDatasetKey);
  }

  public static EditorialDecision create(int subjectDatasetKey) {
    EditorialDecision d = new EditorialDecision();
    d.setDatasetKey(Datasets.COL);
    d.setSubjectDatasetKey(subjectDatasetKey);
    d.setSubject(TestEntityGenerator.newSimpleName());
    d.setMode(EditorialDecision.Mode.UPDATE);
    d.setKeepOriginalName(true);
    d.setName(TestEntityGenerator.newName());
    d.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
    d.setExtinct(true);
    d.setEnvironments(Set.of(Environment.MARINE, Environment.BRACKISH));
    d.setNote("I cannot remember why I did this.");
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(d.getCreatedBy());
    return d;
  }
  
  @Override
  EditorialDecision removeDbCreatedProps(EditorialDecision d) {
    return removeCreatedProps(d);
  }

  public static EditorialDecision removeCreatedProps(EditorialDecision d) {
    d.setOriginalSubjectId(null);
    d.getSubject().setBroken(false);
    return d;
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/674
   */
  @Test
  public void updateNameType() throws Exception {
    EditorialDecision d = ApiModule.MAPPER.readValue(Resources.stream("json/decision1.json"), EditorialDecision.class);
    assertNull(d.getName().getRank());
    assertFalse(d.getName().isCandidatus());
    d.applyUser(TestEntityGenerator.USER_USER);
    mapper().create(d);

    EditorialDecision d2 = mapper().get(d);

    assertEquals(removeDbCreatedProps(d2), removeDbCreatedProps(d));
  }

  @Test
  public void listProjectKeys(){
    // just test valid sql rather than expected outcomes
    mapper().listProjectKeys(appleKey);
  }

  @Test
  public void listStaleAmbiguousUpdateDecisions(){
    // just test valid sql rather than expected outcomes
    mapper().listStaleAmbiguousUpdateDecisions(appleKey, null, 100);
    mapper().listStaleAmbiguousUpdateDecisions(appleKey, 1, 100);
  }

  @Test
  public void facets(){
    var ed = create(appleKey);
    mapper().create(ed);

    var req = new DecisionSearchRequest();
    req.setDatasetKey(Datasets.COL);
    var resp = mapper().searchModeFacet(req);
    assertEquals(1, resp.size());
    assertEquals(1, resp.get(0).getCount());
    assertEquals(ed.getMode(), resp.get(0).getValue());

    req.setSubjectDatasetKey(appleKey);
    resp = mapper().searchModeFacet(req);
    assertEquals(1, resp.size());

    req.setRank(Rank.SEROVAR);
    resp = mapper().searchModeFacet(req);
    assertEquals(0, resp.size());
  }

  @Test
  public void process(){
    // processing
    CountHandler handler = new CountHandler();
    mapper().processDataset(catalogeKey).forEach(handler);
    assertEquals(0, handler.counter.size());
  }
  
  public static class CountHandler<T extends DataEntity<Integer> & DatasetScoped> implements Consumer<T> {
    Map<Integer, Integer> counter = new HashMap<>();
  
    @Override
    public void accept(T d) {
      if (counter.containsKey(d.getDatasetKey())) {
        counter.put(d.getDatasetKey(), counter.get(d.getDatasetKey()) + 1);
      } else {
        counter.put(d.getDatasetKey(), 1);
      }
    }
  }

}