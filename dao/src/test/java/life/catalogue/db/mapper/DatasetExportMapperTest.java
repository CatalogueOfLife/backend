package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.db.TestDataRule;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetExportMapperTest extends CRUDTestBase<UUID, DatasetExport, DatasetExportMapper> {

  public DatasetExportMapperTest() {
    super(DatasetExportMapper.class);
  }

  public static DatasetExport create(JobStatus status) {
    ExportRequest req = new ExportRequest();
    req.setDatasetKey(datasetKey);
    req.setFormat(DataFormat.COLDP);
    req.setRoot(new SimpleName("root", "Abies alba", Rank.SPECIES));
    req.setExcel(true);
    req.setSynonyms(true);
    req.setMinRank(Rank.SPECIES);

    DatasetExport d = new DatasetExport();
    d.setKey(UUID.randomUUID());
    d.setAttempt(1);
    d.setStatus(status);
    d.setRequest(req);
    d.setTaxonCount(1324);
    d.setTruncated(Set.of(ColdpTerm.VernacularName, DwcTerm.Taxon));
    d.getTaxaByRankCount().put(Rank.SPECIES, 100);
    d.getTaxaByRankCount().put(Rank.GENUS, 11);
    d.getTaxaByRankCount().put(Rank.FAMILY, 2);
    d.setClassification(List.of(
      new SimpleName("ABIES", "Abies", Rank.GENUS),
      new SimpleName("PINACEAE", "Pinaceae", Rank.FAMILY),
      new SimpleName("PINALES", "Pinales", Rank.ORDER),
      new SimpleName("PLANTAE", "Plantae", Rank.KINGDOM)
    ));
    d.setCreated(LocalDateTime.now());
    d.setCreatedBy(Users.DB_INIT);
    return d;
  }

  @Test
  public void search() throws Exception {
    final int datasetKey = TestDataRule.APPLE.key;
    var f = new ExportSearchRequest();
    f.setStatus(Set.of(JobStatus.FAILED, JobStatus.CANCELED));
    f.setDatasetKey(datasetKey);
    f.setExcel(true);
    f.setSynonyms(false);
    f.setTaxonID("abcde");
    f.setMinRank(Rank.BIOVAR);
    var res = mapper().search(f, new Page());
    assertNotNull(res);
    assertTrue(res.isEmpty());

    // create 4 exports
    for (DataFormat df : List.of(DataFormat.ACEF,DataFormat.DWCA,DataFormat.TEXT_TREE,DataFormat.COLDP)) {
      var exp = createTestEntity(datasetKey);
      ExportRequest req = new ExportRequest();
      req.setDatasetKey(datasetKey);
      req.setFormat(df);
      exp.setRequest(req);
      mapper().create(exp);
    }
    Page p = new Page(0, 25);
    var filter = ExportSearchRequest.fullDataset(TestDataRule.APPLE.key);
    res = mapper().search(filter, p);
    assertNotNull(res);
    assertFalse(res.isEmpty());
    assertEquals(4, res.size());
    var resp = new ResultPage<>(p, res, () -> mapper().count(filter));
    assertEquals(4, resp.getResult().size());
  }

  @Test
  public void deleted() throws Exception {
    DatasetExport e = createTestEntity(datasetKey);
    mapper().create(e);
    commit();
    assertNull(mapper().get(e.getKey()).getDeleted());

    mapper().delete(e.getKey());
    commit();

    assertNotNull(mapper().get(e.getKey()).getDeleted());
  }

  @Override
  DatasetExport createTestEntity(int datasetKey) {
    return create(JobStatus.FINISHED);
  }

  @Override
  void updateTestObj(DatasetExport obj) {
    obj.setTaxonCount(999912);
    obj.getRequest().getRoot().setId("9ewufczbz");
  }
}