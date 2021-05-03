package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.concurrent.JobStatus;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DatasetExportMapperTest extends CRUDTestBase<UUID, DatasetExport, DatasetExportMapper> {

  public DatasetExportMapperTest() {
    super(DatasetExportMapper.class);
  }

  private static DatasetExport create(JobStatus status) {
    ExportRequest req = new ExportRequest();
    req.setDatasetKey(datasetKey);
    req.setFormat(DataFormat.COLDP);
    req.setTaxonID("root");
    DatasetExport d = new DatasetExport();
    d.setKey(UUID.randomUUID());
    d.setImportAttempt(1);
    d.setStatus(status);
    d.setRequest(req);
    d.setTaxonCount(1324);
    d.getTaxaByRankCount().put(Rank.SPECIES, 100);
    d.getTaxaByRankCount().put(Rank.GENUS, 11);
    d.getTaxaByRankCount().put(Rank.FAMILY, 2);

    d.setCreated(LocalDateTime.now());
    d.setCreatedBy(Users.DB_INIT);
    return d;
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
    obj.getRequest().setTaxonID("9ewufczbz");
  }
}