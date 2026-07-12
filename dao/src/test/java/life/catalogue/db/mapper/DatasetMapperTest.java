package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.db.type2.StringCount;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nullable;

import static org.junit.Assert.*;

public class DatasetMapperTest extends CRUDEntityTestBase<Integer, Dataset, DatasetMapper> {

  public DatasetMapperTest() {
    super(DatasetMapper.class);
  }

  public static Dataset create() {
    Dataset d = new Dataset();
    populate(d);
    return d;
  }

  public static Dataset populate(Dataset d) {
    d.setPrivat(false);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setGbifKey(UUID.randomUUID());
    d.setGbifPublisherKey(UUID.randomUUID());
    d.applyUser(Users.DB_INIT);
    d.setType(DatasetType.TAXONOMIC);
    d.setTitle(RandomUtils.randomLatinString(80));
    d.setDescription(RandomUtils.randomLatinString(500));
    d.setKeyword(List.of("test", "work", "bio"));
    d.setLicense(License.CC0);
    d.setCreator(new ArrayList<>());
    d.setEditor(new ArrayList<>());
    d.setContributor(new ArrayList<>());
    for (int i = 0; i < 8; i++) {
      d.getCreator().add(Agent.parse(RandomUtils.randomLatinString(100)));
      d.getEditor().add(Agent.parse(RandomUtils.randomLatinString(100)));
      d.getContributor().add(Agent.parse(RandomUtils.randomLatinString(100)));
    }
    d.setContact(Agent.parse("Hans Peter"));
    d.setPublisher(Agent.parse("Peter Publish"));
    d.setIssued(FuzzyDate.now());
    d.setVersion("v123");
    d.setUrl(URI.create("https://www.gbif.org/dataset/" + d.getVersion()));
    d.setFeedbackUrl(URI.create("https://www.github.com/xyz/123/issues"));
    d.setIdentifier(new ArrayList<>(List.of(
      new Identifier(DOI.test(UUID.randomUUID().toString())),
      new Identifier("col","1001")
    )));
    d.setUrlFormatter(Map.of(
      "name", "http://" + RandomUtils.randomLatinString(8) + ".org/name/{ID}",
      "reference", "https://fishbase.mnhn.fr/references/FBRefSummary.php?ID={ID}"
    ));
    d.setConversion(new Dataset.UrlDescription("http://www.gbif.org/readme", "My first instructions how to read"));
    d.setNotes("my notes");
    d.setDoi(DOI.test(UUID.randomUUID().toString()));
    d.setSize(0);
    // we dont add source citations as the DatasetMapper does not persist them
    // this is done in the DAO only and should be tested there!
    return d;
  }

  @Test
  public void settings() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);
    commit();

    DatasetSettings ds = mapper().getSettings(d1.getKey());
    assertNotNull(ds);
    assertTrue(ds.isEmpty());

    ds = new DatasetSettings();
    ds.put(Setting.IMPORT_FREQUENCY, Frequency.MONTHLY);
    ds.put(Setting.DATA_ACCESS, URI.create("https://api.gbif.org/v1/dataset/" + d1.getGbifKey()));
    ds.put(Setting.DATA_FORMAT, DataFormat.ACEF);

    mapper().updateSettings(d1.getKey(), ds, Users.TESTER);
    commit();

    DatasetSettings ds2 = mapper().getSettings(d1.getKey());
    //printDiff(ds2, ds);
    assertEquals(ds2, ds);

    ds.put(Setting.REMATCH_DECISIONS, true);
    ds.remove(Setting.DATA_ACCESS);
    mapper().updateSettings(d1.getKey(), ds, Users.TESTER);

    ds2 = mapper().getSettings(d1.getKey());
    assertEquals(ds2, ds);
  }

  @Test
  public void roundtrip() throws Exception {
    Dataset d1 = create();
    // this also tests the custom PersonTypeHandler and PersonArrayTypeHandler
    // thats why we need lots of strange potentially data that needs to be escaped properly
    d1.getContact().setFamily("O'Hara");
    d1.getContact().setGiven("Œre-Fölíñgé");
    d1.getContact().setEmail("Maxi\t<oere@foo.bar>\nhidden");
    d1.getContact().setOrcid("1234,\"5678\".90/x");
    mapper().create(d1);

    commit();

    Dataset d2 = TestEntityGenerator.nullifyDate(mapper().get(d1.getKey()));

    assertEquals(d1.getContact(), d2.getContact());
    assertEquals(d1, d2);
  }

  @Test
  public void keysAbove() throws Exception {
    assertEquals(3, mapper().keysAbove(1, null).size());
    assertEquals(3, mapper().keysAbove(1, LocalDateTime.now()).size());
    assertEquals(0, mapper().keysAbove(1, LocalDateTime.MIN).size());
    assertEquals(0, mapper().keysAbove(10000, null).size());
  }

  @Test
  public void gbif() throws Exception {
    assertEquals(0, mapper().listGBIF().size());
    assertEquals(0, mapper().listKeysGBIF().size());
  }

  @Test
  public void keysByPublisher() throws Exception {
    assertEquals(0, mapper().keysByPublisher(UUID.randomUUID()).size());
  }

  @Test
  public void privateProjectReleases() throws Exception {
    // use a real user (not a bot) as a creator
    User user = new User();
    user.setUsername("abcd");
    session().getMapper(UserMapper.class).create(user);
    final int ukey = user.getKey();

    Dataset proj = create();
    proj.setOrigin(DatasetOrigin.PROJECT);
    proj.setCreatedBy(ukey);
    mapper().create(proj);
    commit();
    final int projKey = proj.getKey();

    Dataset rel1 = create();
    rel1.setOrigin(DatasetOrigin.RELEASE);
    rel1.setSourceKey(projKey);
    rel1.setCreatedBy(ukey);
    mapper().create(rel1);

    Dataset rel2 = create();
    rel2.setOrigin(DatasetOrigin.RELEASE);
    rel2.setSourceKey(projKey);
    rel2.setPrivat(true);
    mapper().create(rel2);
    commit();

    List<Dataset> resp = mapper().search(null, null, new Page());
    assertEquals(5, resp.size()); // 1 public, 1 private

    final DatasetSearchRequest req = new DatasetSearchRequest();
    req.setReleasedFrom(projKey);

    resp = mapper().search(req, null, new Page());
    assertEquals(1, resp.size()); // 1 public, 1 private

    // this user has access to the project and should therefore have access to all private releases!
    resp = mapper().search(req, ukey, new Page());
    assertEquals(2, resp.size());

    // add a reviewer
    User reviewer = new User();
    reviewer.setUsername("reviewer");
    session().getMapper(UserMapper.class).create(reviewer);
    final int rkey = reviewer.getKey();

    resp = mapper().search(req, rkey, new Page());
    assertEquals(1, resp.size());

    mapper().addReviewer(projKey, rkey, ukey);
    resp = mapper().search(req, rkey, new Page());
    assertEquals(2, resp.size());
    commit();

  }

  /**
   * releasedFrom=COL is special: besides the proper releases of the COL project (matched by source_key)
   * it also lists the older COL releases that were re-imported as EXTERNAL datasets and only carry the
   * COL release alias convention (COLyy[.m][ XR], e.g. COL00, COL19, COL20 XR, COL00.5).
   */
  @Test
  public void releasedFromCol() throws Exception {
    // proper COL releases (origin RELEASE/XRELEASE, source_key=COL) - matched authoritatively by source_key
    Dataset relCol = create();
    relCol.setOrigin(DatasetOrigin.RELEASE);
    relCol.setSourceKey(Datasets.COL);
    relCol.setAlias("COL25");
    mapper().create(relCol);

    Dataset xrCol = create();
    xrCol.setOrigin(DatasetOrigin.XRELEASE);
    xrCol.setSourceKey(Datasets.COL);
    xrCol.setAlias("COL25 XR");
    mapper().create(xrCol);

    // legacy COL releases re-imported as EXTERNAL datasets (no source_key) - matched by alias convention
    Dataset extAnnual = create();
    extAnnual.setOrigin(DatasetOrigin.EXTERNAL);
    extAnnual.setAlias("COL19");
    mapper().create(extAnnual);

    Dataset extAnnualXr = create();
    extAnnualXr.setOrigin(DatasetOrigin.EXTERNAL);
    extAnnualXr.setAlias("COL20 XR");
    mapper().create(extAnnualXr);

    Dataset extMonthly = create();
    extMonthly.setOrigin(DatasetOrigin.EXTERNAL);
    extMonthly.setAlias("COL00.5"); // earliest possible year, monthly
    mapper().create(extMonthly);

    // externals that must NOT be treated as COL releases
    Dataset extOther = create();
    extOther.setOrigin(DatasetOrigin.EXTERNAL);
    extOther.setAlias("WORMS");
    mapper().create(extOther);

    Dataset extTrap = create(); // 4-digit year must not match the anchored pattern
    extTrap.setOrigin(DatasetOrigin.EXTERNAL);
    extTrap.setAlias("COL2019");
    mapper().create(extTrap);

    // a non-COL project with its own release - COL-aliased datasets must not leak here
    Dataset proj2 = create();
    proj2.setOrigin(DatasetOrigin.PROJECT);
    mapper().create(proj2);
    Dataset rel2 = create();
    rel2.setOrigin(DatasetOrigin.RELEASE);
    rel2.setSourceKey(proj2.getKey());
    rel2.setAlias("COL19"); // a release of proj2 even though its alias looks COL-like
    mapper().create(rel2);
    commit();

    final DatasetSearchRequest req = new DatasetSearchRequest();
    req.setReleasedFrom(Datasets.COL);
    Set<Integer> keys = mapper().search(req, null, new Page(0, 100)).stream()
        .map(Dataset::getKey).collect(Collectors.toSet());

    // proper releases + legacy externals matching the alias convention
    assertTrue(keys.contains(relCol.getKey()));
    assertTrue(keys.contains(xrCol.getKey()));
    assertTrue(keys.contains(extAnnual.getKey()));
    assertTrue(keys.contains(extAnnualXr.getKey()));
    assertTrue(keys.contains(extMonthly.getKey()));
    // non-COL externals excluded
    assertFalse(keys.contains(extOther.getKey()));
    assertFalse(keys.contains(extTrap.getKey()));
    // releases of other projects excluded
    assertFalse(keys.contains(rel2.getKey()));

    // releasedFrom for a non-COL project is unchanged: only its own releases, no alias matching
    req.setReleasedFrom(proj2.getKey());
    Set<Integer> keys2 = mapper().search(req, null, new Page(0, 100)).stream()
        .map(Dataset::getKey).collect(Collectors.toSet());
    assertTrue(keys2.contains(rel2.getKey()));
    assertFalse(keys2.contains(extAnnual.getKey()));
    assertFalse(keys2.contains(relCol.getKey()));
  }

  @Test
  public void sortByIssued() throws Exception {
    Dataset d2005 = create();
    d2005.setIssued(FuzzyDate.of("2005"));
    mapper().create(d2005);
    Dataset d2010 = create();
    d2010.setIssued(FuzzyDate.of("2010-06"));
    mapper().create(d2010);
    Dataset d2018 = create();
    d2018.setIssued(FuzzyDate.of("2018-03-15"));
    mapper().create(d2018);
    commit();

    final List<Integer> mine = List.of(d2005.getKey(), d2010.getKey(), d2018.getKey());
    final DatasetSearchRequest req = new DatasetSearchRequest();
    req.setSortBy(DatasetSearchRequest.SortBy.ISSUED);

    // default order is newest issued first, consistent with sortBy=CREATED
    List<Integer> order = mapper().search(req, null, new Page(0, 1000)).stream()
        .map(Dataset::getKey).filter(mine::contains).collect(Collectors.toList());
    assertEquals(List.of(d2018.getKey(), d2010.getKey(), d2005.getKey()), order);

    // reversed gives oldest issued first
    req.setReverse(true);
    List<Integer> reversed = mapper().search(req, null, new Page(0, 1000)).stream()
        .map(Dataset::getKey).filter(mine::contains).collect(Collectors.toList());
    assertEquals(List.of(d2005.getKey(), d2010.getKey(), d2018.getKey()), reversed);
  }

  @Test
  public void isPrivateAndExists() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);
    commit();

    final DatasetSearchRequest req = new DatasetSearchRequest();
    List<Dataset> resp = mapper().search(req, null, new Page());
    assertEquals(4, resp.size());

    assertTrue(mapper().exists(d1.getKey()));
    assertFalse(mapper().exists(-3456));

    assertFalse(d1.isPrivat());
    assertFalse(mapper().isPrivate(d1.getKey()));
    assertFalse(mapper().isPrivate(-528));

    // now add a real user (not a bot) as a creator
    User user = new User();
    user.setUsername("abcd");
    session().getMapper(UserMapper.class).create(user);
    final int ukey = user.getKey();

    d1.setPrivat(true);
    mapper().update(d1);
    mapper().addEditor(d1.getKey(), ukey, Users.DB_INIT);
    commit();

    assertTrue(d1.isPrivat());
    assertTrue(mapper().isPrivate(d1.getKey()));
    assertFalse(mapper().isPrivate(-528));


    // 5 datasets, 1 names index, 3 creator=DB_INIT, 1 creator ukey and private
    resp = mapper().search(req, null, new Page());
    assertEquals(3, resp.size());

    resp = mapper().search(req, Users.DB_INIT, new Page());
    assertEquals(3, resp.size());

    resp = mapper().search(req, Users.TESTER, new Page());
    assertEquals(3, resp.size());

    // this user has access to the private dataset!
    resp = mapper().search(req, ukey, new Page());
    assertEquals(4, resp.size());

    // add editor to private dataset
    mapper().addEditor(d1.getKey(), Users.TESTER, Users.TESTER);
    resp = mapper().search(req, Users.TESTER, new Page());
    assertEquals(4, resp.size());
  }

  @Test
  public void immutableOriginAndSourceKey() throws Exception {
    Dataset d1 = create();
    d1.setSourceKey(Datasets.COL);
    d1.setOrigin(DatasetOrigin.RELEASE);
    mapper().create(d1);
    DatasetOrigin o = d1.getOrigin();
    assertEquals(DatasetOrigin.RELEASE, o);
    assertEquals((Integer)Datasets.COL, d1.getSourceKey());

    d1.setOrigin(DatasetOrigin.PROJECT);
    d1.setSourceKey(null);
    mapper().update(d1);

    commit();

    Dataset d2 = mapper().get(d1.getKey());
    assertEquals(DatasetOrigin.RELEASE, d2.getOrigin());
    assertEquals((Integer)Datasets.COL, d2.getSourceKey());
  }

  /**
   * We only logically delete datasets, dont run super test
   */
  @Test
  @Override
  public void deleted() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);

    commit();

    // not deleted yet
    Dataset d = mapper().get(d1.getKey());
    assertNull(d.getDeleted());
    assertNotNull(d.getCreated());

    // mark deleted
    mapper().delete(d1.getKey());
    d = mapper().get(d1.getKey());
    assertNotNull(d.getDeleted());

    commit();

    // list releases
    Dataset p = create();
    p.setOrigin(DatasetOrigin.PROJECT);
    mapper().create(p);

    Dataset r = create();
    r.setGbifKey(null);
    r.setDoi(null);
    r.setOrigin(DatasetOrigin.RELEASE);
    r.setSourceKey(p.getKey());
    r.setAttempt(0);
    mapper().create(r);
    final int r1 = r.getKey();

    r.setKey(null);
    mapper().create(r);
    final int r2 = r.getKey();

    r.setKey(null);
    mapper().create(r);
    final int r3 = r.getKey();

    var rels = mapper().listReleasesQuick(p.getKey(), true, true);
    assertEquals(3, rels.size());
    assertEquals(0, rels.stream().filter(DatasetRelease::isDeleted).count());

    var r3b = mapper().getRelease(r3);
    var r3Expected = new DatasetRelease(r);
    r3Expected.setAttempt(r.getAttempt());

    assertEquals(r3Expected, r3b);

    mapper().delete(r2);
    rels = mapper().listReleasesQuick(p.getKey(), true, true);
    assertEquals(3, rels.size());
    assertEquals(1, rels.stream().filter(DatasetRelease::isDeleted).count());
  }

  @Test
  public void count() throws Exception {
    assertEquals(3, mapper().count(null, null));

    mapper().create(create());
    mapper().create(create());
    // even thogh not committed we are in the same session so we see the new
    // datasets already
    assertEquals(5, mapper().count(null, null));

    commit();
    assertEquals(5, mapper().count(null, null));

    var req = new DatasetSearchRequest();
    req.setGbifPublisherKeyExclusion(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    assertEquals(5, mapper().count(req, null));
  }

  @Test
  public void keys() throws Exception {
    List<Integer> external = new ArrayList<>();
    Dataset d = create();
    mapper().create(d);
    commit();
    external.add(d.getKey());
    d = create();
    mapper().create(d);
    commit();
    external.add(d.getKey());
    d = create();
    mapper().create(d);
    commit();
    external.add(d.getKey());
    external.add(12); // this is a preexsting dataset
    Collections.sort(external);

    List<Integer> all = mapper().list(new Page(100)).stream().map(Dataset::getKey).collect(Collectors.toList());
    Collections.sort(all);

    List<Integer> actual = mapper().keys(false);
    Collections.sort(actual);
    assertEquals(all, actual);

    actual = mapper().keys(true);
    Collections.sort(actual);
    assertEquals(all, actual);

    actual = mapper().keys(false, DatasetOrigin.EXTERNAL);
    Collections.sort(actual);
    assertEquals(external, actual);

    actual = mapper().keys(false, DatasetOrigin.EXTERNAL, DatasetOrigin.RELEASE);
    Collections.sort(actual);
    assertEquals(external, actual);

    actual = mapper().keys(false, DatasetOrigin.RELEASE);
    assertTrue(actual.isEmpty());

    actual = mapper().keys(false, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT);
    Collections.sort(actual);
    assertEquals(all, actual);

    // also test searchKey
    DatasetSearchRequest dr = new DatasetSearchRequest();
    dr.setQ("Life");
    actual = mapper().searchKeys(dr, DatasetMapper.MAGIC_ADMIN_USER_KEY);
    assertEquals(List.of(Datasets.COL), actual);
  }

  @Test
  public void dupes() throws Exception {
    Dataset d = create();
    mapper().create(d);

    d = create();
    d.setTitle("My life");
    d.setDescription("Something different");
    mapper().create(d);

    for (int i = 0; i < 10; i++) {
      d = create();
      if (i<3) {
        d.setTitle("My life");
      } else {
        d.setTitle("Your life");
      }
      d.setDescription("Always the same");
      mapper().create(d);
    }
    commit();

    var dupes = mapper().duplicates(2, null);
    assertEquals(2, dupes.size());
    assertEquals(3, dupes.get(0).getKeys().size());
    assertEquals(7, dupes.get(1).getKeys().size());

    dupes = mapper().duplicates(5, null);
    assertEquals(1, dupes.size());
    assertEquals(7, dupes.get(0).getKeys().size());

    dupes = mapper().duplicates(10, null);
    assertEquals(0, dupes.size());
  }

  private List<Dataset> createExpected() throws Exception {
    List<Dataset> ds = new ArrayList<>();
    ds.add(mapper().get(Datasets.COL));
    ds.add(mapper().get(TestEntityGenerator.DATASET11.getKey()));
    ds.add(mapper().get(TestEntityGenerator.DATASET12.getKey()));
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());
    return ds;
  }

  @Test
  public void simple() throws Exception {
    for (int key : List.of(Datasets.COL, TestEntityGenerator.DATASET11.getKey(), TestEntityGenerator.DATASET12.getKey())) {
      var d = mapper().get(key);
      var ds = mapper().getSimple(key);
      var ds2 = new DatasetSimple(d);
      assertEquals(ds, ds2);
    }
  }

  @Test
  public void getDois() throws Exception {
    var d = create();
    mapper().create(d);
    commit();
    var dois = mapper().getDois(d.getKey());
    assertEquals(1, dois.size());
    assertEquals(d.getDoi(), dois.getFirst());
  }

  @Test
  public void list() throws Exception {
    List<Dataset> ds = createExpected();
    for (Dataset d : ds) {
      if (d.getKey() == null) {
        mapper().create(d);
      }
      // dont compare created stamps
      d.setCreated(null);
    }
    commit();
    removeDbCreatedProps(ds);

    // get first page
    Page p = new Page(0, 4);

    List<Dataset> res = removeDbCreatedProps(mapper().list(p));
    assertEquals(4, res.size());

    assertEquals(ds.get(0), res.get(0));
    assertEquals(ds.subList(0, 4), res);

    // next page (d5-8)
    p.next();
    res = removeDbCreatedProps(mapper().list(p));
    assertEquals(4, res.size());
    assertEquals(ds.subList(4, 8), res);

    p.next();
    res = removeDbCreatedProps(mapper().list(p));
    assertTrue(res.isEmpty());
  }

  @Test
  public void listNeverImported() throws Exception {
    List<Dataset> ds = createExpected();
    int withURL = 0;
    for (Dataset d : ds) {
      if (d.getKey() == null) {
        mapper().create(d);
        // we need a URL to be considered for imports
        if (withURL++ < 3) {
          DatasetSettings settings = mapper().getSettings(d.getKey());
          settings.put(Setting.DATA_ACCESS, URI.create("http://localhost/dataset-"+d.getKey()));
          mapper().updateSettings(d.getKey(), settings, Users.DB_INIT);
        }
      }
    }
    commit();

    List<DatasetMapper.DatasetAttempt> tobe = mapper().listToBeImported(3, 7);
    assertEquals(0, tobe.size());

    List<DatasetMapper.DatasetAttempt> never = mapper().listNeverImported(3);
    assertEquals(3, never.size());

    // we only have 3 with URLs
    never = mapper().listNeverImported(10);
    assertEquals(3, never.size());
  }

  @Test
  public void countSearchResults() throws Exception {
    createSearchableDataset("BIZ", "markus", "CUIT", "A sentence with worms and stuff");
    createSearchableDataset("ITIS", "markus", "ITIS", "Also contains worms");
    createSearchableDataset("WORMS", "markus", "WORMS", "The Worms dataset");
    createSearchableDataset("FOO", "markus", "BAR", null);
    commit();
    int count = mapper().count(DatasetSearchRequest.byQuery("worms"), null);
    assertEquals(3, count);

    // https://github.com/CatalogueOfLife/checklistbank/issues/1178
    createSearchableDataset("Waarnemingen.be / observations.be - List of species observed in Belgium", "Swinnen, Kristijn", "Natuurpunt Studie", "Imagine a future where dynamically, from year to year, we can track the progression of alien species (AS), identify emerging species, assess their current and future risk and timely inform policy in a seamless data-driven workflow. One that is built on open science and open data infrastructures. By using international biodiversity standards and facilities, we would ensure interoperability, repeatability and sustainability. This would make the process adaptable to future requirements in an evolving IAS policy landscape both locally and internationally. The project Tracking Invasive Alien Species (TrIAS) aims to do this for Belgium. For a full project description, see Vanderhoeven et al. (2017, https://doi.org/10.3897/rio.3.e13414).");
    commit();
    assertEquals(0, mapper().count(DatasetSearchRequest.byQuery("Waarnemingen"), null));
    assertEquals(0, mapper().count(DatasetSearchRequest.byQuery("waarnemingen"), null));
    assertEquals(1, mapper().count(DatasetSearchRequest.byQuery("Waarnemingen.be"), null));
  }

  private void createSector(int datasetKey, int subjectDatasetKey) {
    Sector s = new Sector();
    s.setDatasetKey(datasetKey);
    s.setSubjectDatasetKey(subjectDatasetKey);
    s.setMode(Sector.Mode.ATTACH);
    s.setSubject(TestEntityGenerator.newSimpleName());
    s.setTarget(TestEntityGenerator.newSimpleName());
    s.setNote(RandomUtils.randomUnicodeString(128));
    s.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());

    mapper(SectorMapper.class).create(s);
  }

  @Test
  public void getMaxKey() throws Exception {
    // 3, 11, 12
    assertEquals(12, (int) mapper().getMaxKey(null));
    assertEquals(12, (int) mapper().getMaxKey(20));
    assertEquals(11, (int) mapper().getMaxKey(12));
    assertEquals(3, (int) mapper().getMaxKey(10));
    assertEquals(3, (int) mapper().getMaxKey(5));
  }

  @Test
  public void sourceSuggest() throws Exception {
    final Integer d1 = createSearchableDataset("ITIS", "Mike;Bob", "ITIS", "Also contains worms");
    final Integer d2 = createSearchableDataset("BIZ", "bob;jim", "CUIT", "A sentence with worms and worms");
    final Integer d3 = createSearchableDataset("WORMS", "Bart", "WORMS", "The Worms dataset");
    final Integer d4 = createSearchableDataset("FOO", "bar;Döring", "BAR", null);
    final Integer d5 = createSearchableDataset("WORMS worms", "beard", "WORMS", "Worms with even more worms than worms");
    final Integer d6 = createSearchableDataset("Lista taxonómica de las especies de equinodermos de México", "Solís Marín", null, "La lista taxonómica de equinodermos de México incluye 1438 taxones con estatus válido: un phylum, cinco clases, 35 órdenes, 120 familias, 409 géneros, 34 subgéneros, 797 especies, 35 subespecies, dos variedades; así como 1031 taxones con estatus sinónimo: tres familias, 158 géneros, 20 subgéneros, 829 especies, 4 subespecies, 13 variedades y 4 formas.");
    createSector(Datasets.COL, d1);
    createSector(Datasets.COL, d2);
    createSector(Datasets.COL, d3);
    createSector(Datasets.COL, d4);
    createSector(Datasets.COL, d6);
    commit();

    int limit = 25;
    for (boolean b : List.of(true, false)) {
      for (Integer k : List.of(d1, d2, d3, d4, d6)) {
        assertEquals(k, mapper().suggest(k.toString(), Datasets.COL, b, limit).get(0).getKey());
      }
      // no sector!
      assertTrue(mapper().suggest(d5.toString(), Datasets.COL, b, limit).isEmpty());

      assertTrue(mapper().suggest("qwertz", Datasets.COL, b, limit).isEmpty());
      assertTrue(mapper().suggest("gbif", Datasets.COL, b, limit).isEmpty());
      assertEquals(d1, mapper().suggest("ITI", Datasets.COL, b, limit).get(0).getKey());
      assertEquals(d1, mapper().suggest("itis", Datasets.COL, b, limit).get(0).getKey());

      assertEquals(2, mapper().suggest("worm", null, b, limit).size());
      assertEquals(d3, mapper().suggest("worm", Datasets.COL, b, limit).get(0).getKey());
      assertEquals(d6, mapper().suggest("Lista taxonómica de las especies de equinodermos de México", Datasets.COL, b, limit).get(0).getKey());
      assertEquals(d6, mapper().suggest("Lista taxonomica de las especies de equinodermos de Mexico", Datasets.COL, b, limit).get(0).getKey());
      assertEquals(d6, mapper().suggest("especies de equinodermos de Mexico", Datasets.COL, b, limit).get(0).getKey());
    }

    mapper().delete(d5);
    commit();
    assertEquals(1, mapper().suggest("worm", null, true, limit).size());
    assertEquals(d3, mapper().suggest("worm", Datasets.COL, true, limit).get(0).getKey());
  }

  @Test
  public void search() throws Exception {
    final Integer d1 = createSearchableDataset("ITIS", "Mike;Bob", "ITIS", "Also contains worms");
    commit();

    final Integer d2 = createSearchableDataset("BIZ", "bob;jim", "CUIT", "A sentence with worms and worms");
    commit();

    final Integer d3 = createSearchableDataset("WORMS", "Bart", "WORMS", "The Worms dataset");
    final Integer d4 = createSearchableDataset("FOO", "bar;Döring", "BAR", null);
    final Integer d5 = createSearchableDataset("WORMS worms", "beard", "WORMS", "Worms with even more worms than worms");
    mapper().delete(d5);
    createSector(Datasets.COL, d3);
    createSector(Datasets.COL, d4);
    createSector(d4, d3);
    commit();

    DatasetSearchRequest query = new DatasetSearchRequest();
    query.setCreated(LocalDate.parse("2031-12-31"));
    assertTrue(mapper().search(query, null, new Page()).isEmpty());

    // apple.sql contains one dataset from 2017
    query.setCreated(LocalDate.parse("2018-02-01"));
    assertEquals(6, mapper().search(query, null, new Page()).size());

    query.setCreated(LocalDate.parse("2016-02-01"));
    assertEquals(7, mapper().search(query, null, new Page()).size());

    query.setCreatedBefore(LocalDate.parse("2020-01-01"));
    assertEquals(2, mapper().search(query, null, new Page()).size());

    query.setCreated(LocalDate.parse("2018-02-01"));
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query.setIssued(FuzzyDate.of("2007-11-21"));
    query.setModified(LocalDate.parse("2031-12-31"));
    assertEquals(0, mapper().search(query, null, new Page()).size());


    // check different orderings
    query = DatasetSearchRequest.byQuery("worms");
    for (DatasetSearchRequest.SortBy by : DatasetSearchRequest.SortBy.values()) {
      query.setSortBy(by);
      List<Dataset> datasets = mapper().search(query, null, new Page());
      assertEquals(3, datasets.size());
      datasets.forEach(c -> Assert.assertNotEquals("FOO", c.getTitle()));
      switch (by) {
        case CREATED:
        case RELEVANCE:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case TITLE:
          assertEquals("Bad ordering by " + by, d2, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case CREATOR:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case KEY:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
        case SIZE:
        case MODIFIED:
          // nothing, from import and all null
      }
    }

    // now try reversed
    query.setReverse(true);
    for (DatasetSearchRequest.SortBy by : DatasetSearchRequest.SortBy.values()) {
      query.setSortBy(by);
      List<Dataset> datasets = mapper().search(query, null, new Page());
      assertEquals(3, datasets.size());
      switch (by) {
        case CREATED:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case RELEVANCE:
          // relevance cannot be reverted
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case TITLE:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(2).getKey());
          break;
        case CREATOR:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case KEY:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
        case SIZE:
        case MODIFIED:
          // nothing, from import and all null
      }
    }


    query = DatasetSearchRequest.byQuery("worms");
    query.setContributesTo(Datasets.COL);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query.setQ(null);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    query.setContributesTo(d4);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    // non existing catalogue
    query.setContributesTo(99);
    assertEquals(0, mapper().search(query, null, new Page()).size());

    query.setContributesTo(Datasets.COL);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    // by source dataset
    query = new DatasetSearchRequest();
    query.setHasSourceDataset(d3);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    query.setHasSourceDataset(d4);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query.setHasSourceDataset(99); // non existing
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // create another catalogue to test non draft sectors
    Dataset cat = TestEntityGenerator.newDataset("cat2");
    TestEntityGenerator.setUser(cat);
    mapper(DatasetMapper.class).create(cat);
    mapper(DatasetPartitionMapper.class).createSequences(cat.getKey());
    // new sectors
    createSector(cat.getKey(), d1);
    createSector(cat.getKey(), d5);
    commit();

    // old query should still be the same
    query = DatasetSearchRequest.byQuery("worms");
    query.setContributesTo(Datasets.COL);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query = new DatasetSearchRequest();
    query.setContributesTo(Datasets.COL);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    query = new DatasetSearchRequest();
    query.setContributesTo(cat.getKey());
    // d5 was deleted!
    assertEquals(1, mapper().search(query, null, new Page()).size());

    // by origin
    query = new DatasetSearchRequest();
    query.setOrigin(List.of(DatasetOrigin.PROJECT));
    assertEquals(7, mapper().search(query, null, new Page()).size());

    query.setOrigin(List.of(DatasetOrigin.EXTERNAL));
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query.setOrigin(List.of(DatasetOrigin.PROJECT, DatasetOrigin.EXTERNAL));
    assertEquals(8, mapper().search(query, null, new Page()).size());

    // by code
    query = new DatasetSearchRequest();
    query.setCode(NomCode.CULTIVARS);
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // by release
    query = new DatasetSearchRequest();
    query.setReleasedFrom(Datasets.COL);
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // private only
    query = new DatasetSearchRequest();
    query.setPrivat(true);
    assertEquals(0, mapper().search(query, null, new Page()).size());
    query.setPrivat(false);
    assertEquals(8, mapper().search(query, null, new Page()).size());

    // rowType queries
    query = new DatasetSearchRequest();
    query.setRowType(List.of(ColdpTerm.TypeMaterial));
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // Umlauts in query
    query = new DatasetSearchRequest();
    query.setQ("Döring");
    var res = mapper().search(query, null, new Page());
    assertEquals(d4, res.get(0).getKey());

    // lastImportState
    query = new DatasetSearchRequest();
    assertEquals(8, mapper().search(query, null, new Page()).size());
    query.setLastImportState(JobStatus.FAILED);
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // tax group
    query = new DatasetSearchRequest();
    query.setGroup(List.of(TaxGroup.Animals, TaxGroup.Plants));
    assertEquals(0, mapper().search(query, null, new Page()).size());

    mapper().updateTaxonomicGroupScope(d1, Set.of(TaxGroup.Animals, TaxGroup.Arthropods, TaxGroup.Insects, TaxGroup.Coleoptera));
    mapper().updateTaxonomicGroupScope(d2, Set.of(TaxGroup.Plants, TaxGroup.Gymnosperms));
    mapper().updateTaxonomicGroupScope(d3, Set.of(TaxGroup.Viruses));
    assertEquals(2, mapper().search(query, null, new Page()).size());
  }

  @Test
  public void searchSimple() throws Exception {
    final Integer d1 = createSearchableDataset("ITIS", "Mike;Bob", "ITIS", "Also contains worms");
    commit();

    final Integer d2 = createSearchableDataset("BIZ", "bob;jim", "CUIT", "A sentence with worms and worms");
    commit();

    final Integer d3 = createSearchableDataset("WORMS", "Bart", "WORMS", "The Worms dataset");
    final Integer d4 = createSearchableDataset("FOO", "bar;Döring", "BAR", null);
    final Integer d5 = createSearchableDataset("WORMS worms", "beard", "WORMS", "Worms with even more worms than worms");
    mapper().delete(d5);
    createSector(Datasets.COL, d3);
    createSector(Datasets.COL, d4);
    createSector(d4, d3);
    commit();

    DatasetSearchRequest query = new DatasetSearchRequest();
    query.setCreated(LocalDate.parse("2031-12-31"));
    assertTrue(mapper().searchSimple(query, null, new Page()).isEmpty());

    // apple.sql contains one dataset from 2017
    query.setCreated(LocalDate.parse("2018-02-01"));
    assertEquals(6, mapper().searchSimple(query, null, new Page()).size());

    query.setCreated(LocalDate.parse("2016-02-01"));
    assertEquals(7, mapper().searchSimple(query, null, new Page()).size());

    query.setCreatedBefore(LocalDate.parse("2020-01-01"));
    assertEquals(2, mapper().searchSimple(query, null, new Page()).size());

    query.setCreated(LocalDate.parse("2018-02-01"));
    assertEquals(1, mapper().searchSimple(query, null, new Page()).size());

    query.setIssued(FuzzyDate.of("2007-11-21"));
    query.setModified(LocalDate.parse("2031-12-31"));
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());


    // check different orderings
    query = DatasetSearchRequest.byQuery("worms");
    for (DatasetSearchRequest.SortBy by : DatasetSearchRequest.SortBy.values()) {
      query.setSortBy(by);
      List<Dataset> datasets = mapper().searchSimple(query, null, new Page());
      assertEquals(3, datasets.size());
      datasets.forEach(c -> Assert.assertNotEquals("FOO", c.getTitle()));
      switch (by) {
        case CREATED:
        case RELEVANCE:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case TITLE:
          assertEquals("Bad ordering by " + by, d2, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case CREATOR:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case KEY:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
        case SIZE:
        case MODIFIED:
          // nothing, from import and all null
      }
    }

    // now try reversed
    query.setReverse(true);
    for (DatasetSearchRequest.SortBy by : DatasetSearchRequest.SortBy.values()) {
      query.setSortBy(by);
      List<Dataset> datasets = mapper().searchSimple(query, null, new Page());
      assertEquals(3, datasets.size());
      switch (by) {
        case CREATED:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case RELEVANCE:
          // relevance cannot be reverted
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case TITLE:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(2).getKey());
          break;
        case CREATOR:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case KEY:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
        case SIZE:
        case MODIFIED:
          // nothing, from import and all null
      }
    }


    query = DatasetSearchRequest.byQuery("worms");
    query.setContributesTo(Datasets.COL);
    assertEquals(1, mapper().searchSimple(query, null, new Page()).size());

    query.setQ(null);
    assertEquals(2, mapper().searchSimple(query, null, new Page()).size());

    query.setContributesTo(d4);
    assertEquals(1, mapper().searchSimple(query, null, new Page()).size());

    // non existing catalogue
    query.setContributesTo(99);
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());

    query.setContributesTo(Datasets.COL);
    assertEquals(2, mapper().searchSimple(query, null, new Page()).size());

    // by source dataset
    query = new DatasetSearchRequest();
    query.setHasSourceDataset(d3);
    assertEquals(2, mapper().searchSimple(query, null, new Page()).size());

    query.setHasSourceDataset(d4);
    assertEquals(1, mapper().searchSimple(query, null, new Page()).size());

    query.setHasSourceDataset(99); // non existing
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());

    // create another catalogue to test non draft sectors
    Dataset cat = TestEntityGenerator.newDataset("cat2");
    TestEntityGenerator.setUser(cat);
    mapper(DatasetMapper.class).create(cat);
    mapper(DatasetPartitionMapper.class).createSequences(cat.getKey());
    // new sectors
    createSector(cat.getKey(), d1);
    createSector(cat.getKey(), d5);
    commit();

    // old query should still be the same
    query = DatasetSearchRequest.byQuery("worms");
    query.setContributesTo(Datasets.COL);
    assertEquals(1, mapper().searchSimple(query, null, new Page()).size());

    query = new DatasetSearchRequest();
    query.setContributesTo(Datasets.COL);
    assertEquals(2, mapper().searchSimple(query, null, new Page()).size());

    query = new DatasetSearchRequest();
    query.setContributesTo(cat.getKey());
    // d5 was deleted!
    assertEquals(1, mapper().searchSimple(query, null, new Page()).size());

    // by origin
    query = new DatasetSearchRequest();
    query.setOrigin(List.of(DatasetOrigin.PROJECT));
    assertEquals(7, mapper().searchSimple(query, null, new Page()).size());

    query.setOrigin(List.of(DatasetOrigin.EXTERNAL));
    assertEquals(1, mapper().searchSimple(query, null, new Page()).size());

    query.setOrigin(List.of(DatasetOrigin.PROJECT, DatasetOrigin.EXTERNAL));
    assertEquals(8, mapper().searchSimple(query, null, new Page()).size());

    // by code
    query = new DatasetSearchRequest();
    query.setCode(NomCode.CULTIVARS);
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());

    // by release
    query = new DatasetSearchRequest();
    query.setReleasedFrom(Datasets.COL);
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());

    // private only
    query = new DatasetSearchRequest();
    query.setPrivat(true);
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());
    query.setPrivat(false);
    assertEquals(8, mapper().searchSimple(query, null, new Page()).size());

    // rowType queries
    query = new DatasetSearchRequest();
    query.setRowType(List.of(ColdpTerm.TypeMaterial));
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());

    // Umlauts in query
    query = new DatasetSearchRequest();
    query.setQ("Döring");
    var res = mapper().search(query, null, new Page());
    assertEquals(d4, res.get(0).getKey());

    // lastImportState
    query = new DatasetSearchRequest();
    assertEquals(8, mapper().searchSimple(query, null, new Page()).size());
    query.setLastImportState(JobStatus.FAILED);
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());

    // tax group
    query = new DatasetSearchRequest();
    query.setGroup(List.of(TaxGroup.Animals, TaxGroup.Plants));
    assertEquals(0, mapper().searchSimple(query, null, new Page()).size());

    mapper().updateTaxonomicGroupScope(d1, Set.of(TaxGroup.Animals, TaxGroup.Arthropods, TaxGroup.Insects, TaxGroup.Coleoptera));
    mapper().updateTaxonomicGroupScope(d2, Set.of(TaxGroup.Plants, TaxGroup.Gymnosperms));
    mapper().updateTaxonomicGroupScope(d3, Set.of(TaxGroup.Viruses));
    assertEquals(2, mapper().searchSimple(query, null, new Page()).size());
  }

  private int createSearchableDataset(String title, String author, String organisation, String description) {
    return createSearchableDataset(title, author, organisation, description, DatasetOrigin.PROJECT, null, null).getKey();
  }

  private Dataset createSearchableDataset(String title, String author, String organisation, String description,
                            DatasetOrigin origin, @Nullable Integer sourceKey, @Nullable DOI doi
  ) {
    Dataset ds = new Dataset();
    ds.setDoi(doi);
    ds.setPrivat(false);
    ds.setTitle(title);
    if (author != null) {
      ds.setCreator(Agent.parse(List.of(author.split(";"))));
    }
    if (organisation != null) {
      ds.setContributor(List.of(Agent.parse(organisation)));
    }
    ds.setDescription(description);
    ds.setType(DatasetType.TAXONOMIC);
    ds.setOrigin(origin);
    ds.setContact(Agent.person("Frank", "Furter", "frank@mailinator.com", "0000-0003-0857-1679"));
    ds.setEditor(List.of(
      Agent.person("Karl", "Marx", "karl@mailinator.com", "0000-0000-0000-0001"),
      Agent.person("Chuck", "Berry", "chuck@mailinator.com", "0000-0666-0666-0666")
    ));

    ds.setSourceKey(sourceKey);
    mapper().create(TestEntityGenerator.setUserDate(ds));

    mapper(DatasetPartitionMapper.class).createSequences(ds.getKey());
    return ds;
  }

  @Test
  public void searchNull() throws Exception {
    final Integer d1 = createSearchableDataset("ITIS", "Mike;Bob", "ITIS", "Also contains worms");
    final Integer d2 = createSearchableDataset("BIZ", "bob;jim", "CUIT", "A sentence with worms and worms");
    final Integer d3 = createSearchableDataset("WORMS", "Bart", "WORMS", "The Worms dataset");
    commit();

    DatasetSettings ds = new DatasetSettings();
    ds.put(Setting.EXTINCT, Rank.GENUS);
    ds.put(Setting.NOMENCLATURAL_CODE, NomCode.ZOOLOGICAL);
    mapper().updateSettings(d1, ds, Users.TESTER);

    DatasetSearchRequest query = new DatasetSearchRequest();
    query.setCode(NomCode.ZOOLOGICAL);
    assertEquals(1, mapper().search(query, null, new Page()).size());
    assertEquals(d1, mapper().search(query, null, new Page()).get(0).getKey());

    query = new DatasetSearchRequest();
    query.setCodeIsNull(true);
    var resp = mapper().search(query, null, new Page());
    assertEquals(5, resp.size()); // 2+3 from apple.sql
  }

  @Test
  public void doi() throws Exception {
    final var d1 = createSearchableDataset("ITIS", "Mike;Bob", "ITIS", "Also contains worms",
      DatasetOrigin.RELEASE, Datasets.COL, DOI.test("123456-a")
    );
    final var d2 = createSearchableDataset("BIZ", "bob;jim", "CUIT", "A sentence with worms and worms",
      DatasetOrigin.RELEASE, Datasets.COL, DOI.test("123456-b")
    );
    final var d3 = createSearchableDataset("WORMS", "Bart", "WORMS", "The Worms dataset",
      DatasetOrigin.RELEASE, Datasets.COL, DOI.test("123456-c")
    );

    commit();

    var req = new DatasetSearchRequest();
    req.setQ(DOI.test("123456-b").getDoiName());
    var d2b = mapper().search(req, 1, new Page()).get(0);
    assertEquals(removeDbCreatedProps(d2), removeDbCreatedProps(d2b));
    var dm = mapper();
    var pk = dm.previousRelease(d2.getKey());
    var nk = dm.nextRelease(d2.getKey());
    assertEquals(removeDbCreatedProps(d1), removeDbCreatedProps(dm.get(pk)));
    assertEquals(removeDbCreatedProps(d3), removeDbCreatedProps(dm.get(nk)));
  }

  @Test
  public void updateLastImport() throws Exception {
    var d = createTestEntity();
    mapper().create(d);
    mapper().updateLastImport(d.getKey(), 2, null);
    mapper().updateLastImport(d.getKey(), 3, DOI.test("test"));
    mapper().updateLastImport(d.getKey(), 13, DOI.test("test13"));
  }

  @Test
  public void publisherNameFilter() throws Exception {
    Dataset d1 = create();
    d1.setPublisher(Agent.organisation("Royal Botanic Gardens, Kew"));
    mapper().create(d1);
    Dataset d2 = create();
    d2.setPublisher(Agent.organisation("Royal Botanic Gardens, Kew"));
    mapper().create(d2);
    Dataset d3 = create();
    d3.setPublisher(Agent.organisation("Missouri Botanical Garden"));
    mapper().create(d3);
    commit();

    // exact match, case-insensitive
    DatasetSearchRequest req = new DatasetSearchRequest();
    req.setPublisher("royal botanic gardens, kew");
    Set<Integer> keys = mapper().search(req, null, new Page(0, 100)).stream()
      .map(Dataset::getKey).collect(Collectors.toSet());
    assertEquals(Set.of(d1.getKey(), d2.getKey()), keys);

    // a substring of the name must NOT match (exact-only)
    req.setPublisher("Kew");
    assertTrue(mapper().search(req, null, new Page(0, 100)).isEmpty());

    // hasFilter reflects the new field
    DatasetSearchRequest hf = new DatasetSearchRequest();
    assertFalse(hf.hasFilter());
    hf.setPublisher("x");
    assertTrue(hf.hasFilter());
  }


  private static Map<String, Integer> toCountMap(List<StringCount> list) {
    Map<String, Integer> m = new HashMap<>();
    for (StringCount sc : list) {
      m.put(sc.getKey(), sc.getCount());
    }
    return m;
  }

  @Test
  public void suggestPublishers() throws Exception {
    Dataset d1 = create();
    d1.setPublisher(Agent.organisation("Royal Botanic Gardens, Kew"));
    mapper().create(d1);
    Dataset d2 = create();
    d2.setPublisher(Agent.organisation("Royal Botanic Gardens, Kew"));
    mapper().create(d2);
    Dataset d3 = create();
    d3.setPublisher(Agent.organisation("Missouri Botanical Garden"));
    mapper().create(d3);
    Dataset d4 = create();
    d4.setPublisher(Agent.organisation("Naturalis Biodiversity Center"));
    mapper().create(d4);
    commit();

    // case-insensitive substring match; counts; Naturalis excluded
    List<StringCount> res = mapper().suggestPublishers("BOTAN", 25, null);
    Map<String, Integer> m = toCountMap(res);
    assertEquals(Integer.valueOf(2), m.get("Royal Botanic Gardens, Kew"));
    assertEquals(Integer.valueOf(1), m.get("Missouri Botanical Garden"));
    assertFalse(m.containsKey("Naturalis Biodiversity Center"));
    // ordered by count desc: Kew (2) before Missouri (1)
    List<String> ordered = res.stream().map(StringCount::getKey)
      .filter(k -> k.contains("Botanic") || k.contains("Botanical"))
      .collect(Collectors.toList());
    assertEquals(List.of("Royal Botanic Gardens, Kew", "Missouri Botanical Garden"), ordered);

    // private dataset publisher hidden from anonymous, visible to its editor
    User u = new User();
    u.setUsername("keweditor");
    session().getMapper(UserMapper.class).create(u);
    final int ukey = u.getKey();
    Dataset dp = create();
    dp.setPublisher(Agent.organisation("Secret Botanic Society"));
    dp.setPrivat(true);
    mapper().create(dp);
    mapper().addEditor(dp.getKey(), ukey, Users.DB_INIT);
    commit();

    assertFalse(toCountMap(mapper().suggestPublishers("Botanic", 25, null))
      .containsKey("Secret Botanic Society"));
    assertEquals(Integer.valueOf(1),
      toCountMap(mapper().suggestPublishers("Botanic", 25, ukey))
        .get("Secret Botanic Society"));
  }

  @Override
  Dataset createTestEntity(int dkey) {
    return create();
  }

  @Override
  Dataset removeDbCreatedProps(Dataset d) {
    return rmDbCreatedProps(d);
  }

  public static Dataset rmDbCreatedProps(Dataset d) {
    d.setSize(0);
    return d;
  }

  @Override
  void updateTestObj(Dataset d) {
    d.setDescription("brand new thing");
  }
}
