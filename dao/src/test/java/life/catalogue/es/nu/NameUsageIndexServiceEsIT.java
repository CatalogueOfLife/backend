package life.catalogue.es.nu;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.EditorialDecision.Mode;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.DecisionDao;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.es.*;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.matching.nidx.NameIndexFactory;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static java.util.stream.Collectors.toList;
import static life.catalogue.junit.PgSetupRule.getSqlSessionFactory;
import static org.junit.Assert.*;

/*
 * Full round-trips into Postgres via DAOs, out of Postgres via the NameUsageWrapperMapper, into Elasticsearch via the NameUsageIndexService
 * and finally out of Elasticsearch via the NameUsageSearchService.
 */
public class NameUsageIndexServiceEsIT extends EsReadWriteTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceEsIT.class);

  @Test
  public void indexDatasetTaxaOnly() throws IOException {
    // Create, insert (into postgres) and return 7 taxa belonging to EsSetupRule.DATASET_KEY
    List<Taxon> pgTaxa = createPgTaxa(7);
    createIndexService().indexDataset(EsSetupRule.DATASET_KEY);
    List<String> ids = pgTaxa.stream().map(Taxon::getId).collect(toList());
    NameUsageSearchResponse res = query(Query.of(q -> q.terms(t -> t
      .field("usageId")
      .terms(v -> v.value(ids.stream().map(FieldValue::of).collect(toList())))
    )));
    List<String> esIds = res.getResult().stream().map(nuw -> nuw.getUsage().getId()).sorted().collect(toList());
    pgTaxa.sort(Comparator.comparing(Taxon::getId));
    List<String> pgIds = pgTaxa.stream().map(Taxon::getId).collect(toList());
    assertEquals(pgIds.size(), esIds.size());
    System.out.println("+++ DIFF TAXA LIST +++");
    for (int i = 0; i < pgIds.size(); i++) {
      System.out.println(" idx="+i);
      assertEquals(pgIds.get(i), esIds.get(i));
    }
  }

  @Test
  public void indexAll() {
    NameUsageIndexService.Stats stats = createIndexService().indexAll();
    assertEquals(4, stats.usages);
    assertEquals(1, stats.names);
  }

  @Test
  public void createEditorialDecision() {
    // Insert 3 taxa into postgres
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(3);
    // Pump them over to Elasticsearch
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    // Make 1st taxon the "subject" of an editorial decision
    Taxon edited = pgTaxa.get(0);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleNameLink.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(Datasets.COL);
    decision.setSubjectDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    // Save the decision to postgres: triggers sync() on the index service
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc, validator);
    dao.create(decision, 0);

    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    request.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, Datasets.COL);
    NameUsageSearchResponse res = search(request);

    assertEquals(1, res.getResult().size());
    assertEquals(edited.getId(), res.getResult().get(0).getUsage().getId());
  }

  @Test
  @Disabled @Ignore
  public void issue407() throws IOException {

    int USER_ID = 10;
    int DATASET_KEY = 11;

    // Extract a taxon from the JSON pasted by thomas into #407.
    InputStream is = getClass().getResourceAsStream("/elastic/Issue407_document.json");
    EsNameUsage doc = EsModule.readDocument(is);
    NameUsageWrapper nuw = NameUsageWrapperConverter.decode(doc.getPayload());
    NameUsageWrapperConverter.enrichPayload(nuw, doc);
    Taxon taxon = (Taxon) nuw.getUsage();

    // Insert that taxon into Postgres
    NameDao ndao = new NameDao(getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    DSID<String> dsid = ndao.create(taxon.getName(), USER_ID);
    LOG.info(">>>>>>> Name inserted into database. ID: {}\n", dsid.getId());
    TaxonDao tdao = new TaxonDao(getSqlSessionFactory(), ndao, null, new ThumborService(new ThumborConfig()), NameUsageIndexService.passThru(), null, validator);
    dsid = tdao.create(taxon, USER_ID);
    LOG.info(">>>>>>> Taxon inserted into database. ID: {}\n", EsModule.writeDebug(taxon));

    // Index the dataset containing the taxon
    NameUsageIndexService svc = createIndexService();
    svc.indexDataset(DATASET_KEY);

    // make sure the decision is empty
    final String taxonId = dsid.getId();
    NameUsageSearchResponse res = query(Query.of(q -> q.term(t -> t.field("usageId").value(taxonId))));
    assertEquals(1, res.getResult().size());
    assertNull(res.getResult().get(0).getDecisions());

    // Now create the decision
    is = getClass().getResourceAsStream("/elastic/Issue407_decision.json");
    EditorialDecision decision = ApiModule.MAPPER.readValue(is, EditorialDecision.class);

    decision.getSubject().setId(taxon.getId());

    DecisionDao ddao = new DecisionDao(getSqlSessionFactory(), svc, validator);
    int key = ddao.create(decision, USER_ID).getId();
    LOG.info(">>>>>>> Decision inserted into database: {}\n", EsModule.writeDebug(decision));

    res = query(Query.of(q -> q.term(t -> t.field("decisionKey").value(key))));
    assertEquals(1, res.getResult().size());
    assertEquals(taxon.getId(), res.getResult().get(0).getUsage().getId());

    final String usageId = dsid.getId();
    res = query(Query.of(q -> q.term(t -> t.field("usageId").value(usageId))));
    assertEquals(1, res.getResult().size());
    assertEquals(key, (int) res.getResult().get(0).getDecisions().get(0).getId());
  }

  @Test
  public void updateEditorialDecision() {
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(3);
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    Taxon edited = pgTaxa.get(0);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleNameLink.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(Datasets.COL);
    decision.setSubjectDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc, validator);
    int key = dao.create(decision, edited.getCreatedBy()).getId();

    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    request.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, Datasets.COL);
    NameUsageSearchResponse res = search(request);

    assertEquals(pgTaxa.get(0).getId(), res.getResult().get(0).getUsage().getId());
    decision.setId(key);
    decision.setSubject(SimpleNameLink.of(pgTaxa.get(1)));
    dao.update(decision, edited.getCreatedBy());

    res = search(request);

    assertEquals(1, res.getResult().size());
    assertEquals(pgTaxa.get(1).getId(), res.getResult().get(0).getUsage().getId());
  }

  @Test
  public void deleteEditorialDecision() throws IOException {
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(4);
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    Taxon edited = pgTaxa.get(2);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleNameLink.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(Datasets.COL);
    decision.setSubjectDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc, validator);
    DSID<Integer> key = dao.create(decision, edited.getCreatedBy());

    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    request.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, Datasets.COL);
    NameUsageSearchResponse res = search(request);

    assertEquals(pgTaxa.get(2).getId(), res.getResult().get(0).getUsage().getId());
    dao.delete(key, 0);
    String usageId = pgTaxa.get(2).getId();
    res = query(Query.of(q -> q.term(t -> t.field("usageId").value(usageId))));
    assertTrue(res.getResult().get(0).getDecisions().isEmpty());
  }

  // Some JSON to send using the REST API
  void printDecision() {
    SimpleNameLink sn = SimpleNameLink.of("s1", "Larus Fuscus", Rank.SPECIES);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(sn);
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(11);
    decision.setCreatedBy(0);
    decision.setModifiedBy(0);
    System.out.println(EsModule.writeDebug(decision));
  }

  private static void massageTaxa(List<Taxon> taxa) {
    taxa.forEach(t -> {
      t.setCreated(null);
      t.setModified(null);
      t.getName().setCreated(null);
      t.getName().setModified(null);
    });
    taxa.sort(Comparator.comparing(Taxon::getId));
  }

}
