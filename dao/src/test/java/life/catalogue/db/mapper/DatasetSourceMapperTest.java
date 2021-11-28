package life.catalogue.db.mapper;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.Datasets;

import java.util.List;

import life.catalogue.api.vocab.Setting;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class DatasetSourceMapperTest extends MapperTestBase<DatasetSourceMapper> {

  public DatasetSourceMapperTest() {
    super(DatasetSourceMapper.class);
  }

  public static Dataset createProjectSource() {
    Dataset d = new Dataset();
    DatasetMapperTest.populate(d);
    d.setSourceKey(Datasets.COL);
    d.setAttempt(3);
    d.setGbifPublisherKey(null);
    d.setGbifKey(null);
    d.setSize(null);
    d.setSource(List.of(
      CitationTest.create(),
      CitationTest.create()
    ));
    return d;
  }

  @Test
  public void deleteByProject() throws Exception {
    mapper().deleteByRelease(Datasets.COL);
  }

  @Test
  public void listProjectSources() throws Exception {
    mapper().listProjectSources(Datasets.COL);
  }

  @Test
  public void listReleaseSources() throws Exception {
    mapper().listReleaseSources(Datasets.COL);
  }

  void persistDatasetCitations(Dataset d){
    var cm = mapper(CitationMapper.class);
    for (var c : d.getSource()) {
      cm.create(d.getKey(), c);
    }
  }

  @Test
  public void roundtripProject() throws Exception {
    // the project source dataset is not archived, just a regular dataset
    Dataset d = createProjectSource();
    mapper(DatasetMapper.class).create(d);
    // persist source citations, sth the DatasetDao normally does
    persistDatasetCitations(d);
    commit();

    Dataset d2 = mapper().getProjectSource(d.getKey(), Datasets.COL);
    // no import attempt expected as there are no synced sectors
    d.setAttempt(null);

    // COL container
    Dataset col = mapper(DatasetMapper.class).get(Datasets.COL);
    assertNull(col.getContainerKey());
    assertNull(col.getContainerTitle());
    assertNull(col.getContainerCreator());
    d.setContainerKey(col.getKey());
    d.setContainerTitle(col.getTitle());
    d.setContainerCreator(col.getCreator());

    commit();
    assertEquals(d2, d);
  }

  @Test
  public void roundtripRelease() throws Exception {
    var cm = mapper(CitationMapper.class);
    var dm = mapper(DatasetMapper.class);

    // add creators to col dataset
    Dataset col = dm.get(Datasets.COL);
    col.setCreator(List.of(
      Agent.person("Afred", "Biolek"),
      Agent.person("Afred", "Mansun"),
      Agent.person("Afred", "Rodriguéz"),
      Agent.person("Ali", "Mohammed"),
      Agent.person("Aaron", "Price")
    ));
    dm.update(col);

    // source dataset
    Dataset s = createProjectSource();
    dm.create(s);
    // persist source citations, sth the DatasetDao normally does
    persistDatasetCitations(s);
    // attempt is updated separately, but needed to copy citations into a release
    dm.updateLastImport(s.getKey(), s.getAttempt());

    // archived source dataset
    mapper(DatasetArchiveMapper.class).create(s.getKey());
    cm.createArchive(s.getKey());

    // release source
    Dataset rs = new Dataset(s);
    mapper().create(Datasets.COL, rs);
    // persist source citations for release
    cm.createRelease(rs.getKey(), Datasets.COL, rs.getAttempt());

    Dataset rs2 = removeDbCreatedProps(mapper().getReleaseSource(rs.getKey(), Datasets.COL));
    commit();

    // COL container
    rs.setContainerKey(col.getKey());
    rs.setContainerTitle(col.getTitle());
    rs.setContainerCreator(col.getCreator());
    assertEquals(rs2, rs);

    // now try to list sources
    mapper().listReleaseSources(Datasets.COL);
  }

  Dataset removeDbCreatedProps(Dataset obj) {
    obj.setCreated(null);
    obj.setModified(null);
    return obj;
  }
}
