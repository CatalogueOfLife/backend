package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorPublisher;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class DatasetSourceMapperTest extends MapperTestBase<DatasetSourceMapper> {

  public DatasetSourceMapperTest() {
    super(DatasetSourceMapper.class);
  }

  public static DatasetSourceMapper.SourceDataset createProjectSource() {
    DatasetSourceMapper.SourceDataset d = new DatasetSourceMapper.SourceDataset();
    DatasetMapperTest.populate(d);
    d.setPrivat(true); // the default which is not stored in the archive
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
    mapper().listProjectSources(Datasets.COL, false);
    mapper().listProjectSources(Datasets.COL, true);
  }

  /**
   * dataset_archive is only ever written by PgImport when an external dataset is reimported, so the attempt a sector
   * synced can be missing from it - a project subject has no archived copy at all. The archive branch used to left
   * join, handing out an all NULL dataset with a null key that ProjectRelease then tried to insert into
   * dataset_source. The live metadata has to be used instead.
   */
  @Test
  public void listProjectSourcesWithoutArchivedMetadata() throws Exception {
    var dm = mapper(DatasetMapper.class);
    var sm = mapper(SectorMapper.class);

    // a source synced at attempt 3 that has since moved on to 5, with nothing archived for 3
    Dataset source = DatasetMapperTest.create();
    dm.create(source);
    dm.updateLastImport(source.getKey(), 3, null);

    Sector s = SectorMapperTest.create(DSID.colID("t1"), DSID.of(source.getKey(), "x"));
    sm.create(s);
    sm.updateLastSync(s, 1); // copies dataset_attempt=3 off the source dataset
    dm.updateLastImport(source.getKey(), 5, null);

    // the sector only counts as a source once it has data in the project
    Taxon t = TestEntityGenerator.newTaxon(Datasets.COL, "src1", null, Rank.SPECIES, "Abies alba");
    t.setSectorKey(s.getId());
    t.getName().setSectorKey(s.getId());
    mapper(NameMapper.class).create(t.getName());
    mapper(TaxonMapper.class).create(t);
    commit();

    var sources = mapper().listProjectSources(Datasets.COL, true);
    assertEquals(1, sources.size());
    assertEquals(source.getKey(), sources.get(0).getKey());
    assertEquals(Integer.valueOf(5), sources.get(0).getAttempt());

    var simple = mapper().listProjectSourcesSimple(Datasets.COL, true);
    assertEquals(1, simple.size());
    assertEquals(source.getKey(), simple.get(0).getKey());
    for (boolean inclPublisherSources : new boolean[]{true, false}) {
      assertEquals(mapper().listProjectSourcesSimple(Datasets.COL, inclPublisherSources).size(),
        mapper().countProjectSources(Datasets.COL, inclPublisherSources));
    }
    assertEquals(1, mapper().countProjectSources(Datasets.COL, true));

    assertNotNull(mapper().getProjectSource(source.getKey(), Datasets.COL));
    assertNotNull(mapper().getProjectSourceSimple(source.getKey(), Datasets.COL));
  }

  /**
   * What a release lists when it finishes goes beyond the owners of usages the project lists, which has to stay cheap.
   */
  @Test
  public void listContributingSources() throws Exception {
    var dm = mapper(DatasetMapper.class);
    var sm = mapper(SectorMapper.class);

    Dataset usages = DatasetMapperTest.create();
    usages.setGbifPublisherKey(null);
    dm.create(usages);
    Sector s = SectorMapperTest.create(DSID.colID("t1"), DSID.of(usages.getKey(), "x"));
    sm.create(s);
    Taxon t = TestEntityGenerator.newTaxon(Datasets.COL, "src1", null, Rank.SPECIES, "Abies alba");
    t.setSectorKey(s.getId());
    t.getName().setSectorKey(s.getId());
    mapper(NameMapper.class).create(t.getName());
    mapper(TaxonMapper.class).create(t);

    // a reference only merge sector like BHL
    Dataset references = DatasetMapperTest.create();
    references.setGbifPublisherKey(null);
    dm.create(references);
    createReference(createMergeSector(references));

    // a merge sector that owns nothing and is only credited on a record of somebody else
    Dataset credited = DatasetMapperTest.create();
    credited.setGbifPublisherKey(null);
    dm.create(credited);
    createMergeSector(credited);
    var vsm = mapper(VerbatimSourceMapper.class);
    VerbatimSource vs = new VerbatimSource(Datasets.COL, 100000, null, null, null, null);
    vsm.create(vs);
    vsm.insertSources(vs, DSID.of(credited.getKey(), "x"), Set.of(InfoGroup.PUBLISHED_IN_PAGE_LINK));

    // an article of a sector publisher owning a reference, but no usage
    UUID publisher = UUID.randomUUID();
    SectorPublisher sp = new SectorPublisher();
    sp.setId(publisher);
    sp.setDatasetKey(Datasets.COL);
    sp.setAlias("Plazi");
    sp.setTitle("Plazi");
    sp.applyUser(Users.TESTER);
    mapper(SectorPublisherMapper.class).create(sp);
    Dataset article = DatasetMapperTest.create();
    article.setGbifPublisherKey(publisher);
    dm.create(article);
    createReference(createMergeSector(article));

    // a sector without any data
    Dataset empty = DatasetMapperTest.create();
    empty.setGbifPublisherKey(null);
    dm.create(empty);
    createMergeSector(empty);
    commit();

    assertEquals(Set.of(usages.getKey(), references.getKey(), credited.getKey()), keys(mapper().listContributingSources(Datasets.COL, true)));
    // reading the project is not affected
    assertEquals(Set.of(usages.getKey()), keys(mapper().listProjectSources(Datasets.COL, true)));
    assertEquals(1, mapper().countProjectSources(Datasets.COL, true));
  }

  private Sector createMergeSector(Dataset subject) {
    Sector s = SectorMapperTest.create(DSID.colID("t" + subject.getKey()), DSID.of(subject.getKey(), "x"));
    s.setMode(Sector.Mode.MERGE);
    s.setTarget(null);
    mapper(SectorMapper.class).create(s);
    return s;
  }

  private void createReference(Sector s) {
    Reference r = TestEntityGenerator.newReference("Faune de Madagascar");
    r.setId("r" + s.getId());
    r.setDatasetKey(Datasets.COL);
    r.setSectorKey(s.getId());
    mapper(ReferenceMapper.class).create(r);
  }

  private static Set<Integer> keys(List<DatasetSourceMapper.SourceDataset> sources) {
    return sources.stream().map(Dataset::getKey).collect(Collectors.toSet());
  }

  @Test
  public void listReleaseSources() throws Exception {
    mapper().listReleaseSources(Datasets.COL, false);
    mapper().listReleaseSources(Datasets.COL, true);
  }

  @Test
  public void listProjectSourcesSimple() throws Exception {
    mapper().listProjectSourcesSimple(Datasets.COL, false);
    mapper().listProjectSourcesSimple(Datasets.COL, true);
  }

  @Test
  public void listReleaseSourcesSimple() throws Exception {
    mapper().listReleaseSourcesSimple(Datasets.COL, true);
    mapper().listReleaseSourcesSimple(Datasets.COL, false);
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
    // mapper takes these from the project
    d.setContainerKey(col.getKey());
    d.setContainerTitle(col.getTitle());
    d.setContainerCreator(col.getCreator());
    d.setContainerPublisher(col.getPublisher());
    d.setContainerVersion(col.getVersion());
    d.setContainerIssued(col.getIssued());
    var ds = new DatasetSourceMapper.SourceDataset(d);
    commit();
    assertEquals(d2, ds);
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
    dm.updateLastImport(s.getKey(), s.getAttempt(), null);

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
    // mapper takes these from the project, with only the first 3 creators when not configured
    rs.setContainerKey(col.getKey());
    rs.setContainerTitle(col.getTitle());
    rs.setContainerCreator(col.getCreator().subList(0, 3));
    rs.setContainerPublisher(col.getPublisher());
    rs.setContainerVersion(col.getVersion());
    rs.setContainerIssued(col.getIssued());
    var ds = new DatasetSourceMapper.SourceDataset(rs);
    assertEquals(rs2, ds);

    // now try to list sources
    mapper().listReleaseSources(Datasets.COL, true);
    for (boolean inclPublisherSources : new boolean[]{true, false}) {
      assertEquals(mapper().listReleaseSourcesSimple(Datasets.COL, inclPublisherSources).size(),
        mapper().countReleaseSources(Datasets.COL, inclPublisherSources));
    }
    assertEquals(1, mapper().countReleaseSources(Datasets.COL, true));

    // the slim projection reads the very same dataset_source row
    var simple = mapper().getReleaseSourceSimple(rs.getKey(), Datasets.COL);
    assertNotNull(simple);
    assertEquals(rs.getKey(), simple.getKey());
    assertEquals(rs.getIssued(), simple.getIssued());
    assertEquals(rs.getDoi(), simple.getDoi());
    assertEquals(rs.getUrl(), simple.getUrl());
    assertEquals(rs.getGbifKey(), simple.getGbifKey());
    assertEquals(rs.getAttempt(), simple.getAttempt());
    assertTrue(simple.hasData());

    // limit container authors to just 2 and verify
    DatasetSettings settings = dm.getSettings(Datasets.COL);
    settings.put(Setting.SOURCE_MAX_CONTAINER_AUTHORS, 2);
    dm.updateSettings(Datasets.COL, settings, 1);
    commit();

    rs2 = removeDbCreatedProps(mapper().getReleaseSource(rs.getKey(), Datasets.COL));
    ds.setContainerCreator(col.getCreator().subList(0,2));
    assertEquals(rs2, ds);
  }

  Dataset removeDbCreatedProps(Dataset obj) {
    obj.setCreated(null);
    obj.setModified(null);
    return obj;
  }
}
