package life.catalogue.resources;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import life.catalogue.api.vocab.area.Country;
import life.catalogue.api.vocab.area.Gazetteer;
import life.catalogue.api.vocab.area.GenericArea;
import life.catalogue.parser.AreaLabelLookup;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VocabResourceTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final String SAMPLE = "{\"type\":\"Feature\",\"properties\":{\"name\":\"Baltic Sea\"},\"geometry\":null}";

  @Test
  public void areaGeojsonReturnsFeature() throws Exception {
    File dir = tmp.newFolder();
    Path feature = dir.toPath().resolve("mrgid/features/8371.geojson");
    Files.createDirectories(feature.getParent());
    Files.writeString(feature, SAMPLE);

    var r = new VocabResource(dir, null);
    try (Response resp = r.areaGeojson("mrgid", "8371")) {
      assertEquals(200, resp.getStatus());
      assertEquals("application/geo+json", resp.getMediaType().toString());
      var bytes = writeEntity(resp);
      assertEquals(SAMPLE, new String(bytes));
    }
  }

  @Test
  public void areaGeojsonMissingFileIs404() throws Exception {
    File dir = tmp.newFolder();
    var r = new VocabResource(dir, null);
    assertThrows(NotFoundException.class, () -> r.areaGeojson("mrgid", "does-not-exist"));
  }

  @Test
  public void areaGeojsonUnconfiguredDirIs404() {
    var r = new VocabResource((File) null, null);
    assertThrows(NotFoundException.class, () -> r.areaGeojson("fao", "37.4.1"));
  }

  @Test
  public void areaGeojsonRejectsPathTraversal() throws Exception {
    File dir = tmp.newFolder();
    var r = new VocabResource(dir, null);
    assertThrows(NotFoundException.class, () -> r.areaGeojson("fao", "../../etc/passwd"));
  }

  @Test
  public void areaGeojsonRejectsUnprefixedId() throws Exception {
    File dir = tmp.newFolder();
    var r = new VocabResource(dir, null);
    assertThrows(NotFoundException.class, () -> r.areaGeojson(null, "8371"));
    assertThrows(NotFoundException.class, () -> r.areaGeojson("mrgid", ""));
    assertThrows(NotFoundException.class, () -> r.areaGeojson("", "8371"));
  }

  @Test
  public void areaGeojsonAppliesFilenameNormalization() throws Exception {
    File dir = tmp.newFolder();
    // build-script writes the normalized filename; the resource must resolve to the same one
    Path feature = dir.toPath().resolve("fao/features/37-4-1.geojson");
    Files.createDirectories(feature.getParent());
    Files.writeString(feature, SAMPLE);

    var r = new VocabResource(dir, null);
    // uppercase prefix and `/` in the id portion must normalize to the on-disk filename
    try (Response resp = r.areaGeojson("FAO", "37/4/1")) {
      assertEquals(200, resp.getStatus());
    }
  }

  @Test
  public void areaGeojsonOnlyReadsKnownGazetteers() throws Exception {
    File dir = tmp.newFolder("gazetteers", "dir");
    Path outside = dir.toPath().resolve("../other/features/1.geojson");
    Files.createDirectories(outside.getParent());
    Files.writeString(outside, SAMPLE);

    var r = new VocabResource(dir, null);
    assertThrows(NotFoundException.class, () -> r.areaGeojson("../other", "1"));
    assertThrows(NotFoundException.class, () -> r.areaGeojson("other", "1"));
  }

  @Test
  public void gazetteerFilesAreServed() throws Exception {
    File dir = tmp.newFolder();
    Path iso = dir.toPath().resolve("iso");
    Files.createDirectories(iso);
    Files.writeString(iso.resolve("overview.geojson"), SAMPLE);
    Files.writeString(iso.resolve("build.json"), "{\"label_count\":1}");
    Files.writeString(iso.resolve("labels.tsv"), "DE\tGermany\n");
    Files.writeString(iso.resolve("map.svg"), "<svg/>");

    var r = new VocabResource(dir, null);
    try (Response resp = r.gazetteerOverview("iso")) {
      assertEquals("application/geo+json", resp.getMediaType().toString());
      assertEquals(SAMPLE, new String(writeEntity(resp)));
    }
    try (Response resp = r.gazetteerBuild("ISO")) {
      assertEquals("application/json", resp.getMediaType().toString());
      assertEquals("{\"label_count\":1}", new String(writeEntity(resp)));
    }
    try (Response resp = r.gazetteerLabels("iso")) {
      assertEquals("text/tsv", resp.getMediaType().toString());
      assertEquals("DE\tGermany\n", new String(writeEntity(resp)));
    }
    try (Response resp = r.gazetteerMap("iso")) {
      assertEquals("image/svg+xml", resp.getMediaType().toString());
      assertEquals("<svg/>", new String(writeEntity(resp)));
    }
  }

  @Test
  public void gazetteerFilesMissingAre404() throws Exception {
    File dir = tmp.newFolder();
    Files.createDirectories(dir.toPath().resolve("wdpa"));
    Files.writeString(dir.toPath().resolve("wdpa/build.json"), "{}");

    var r = new VocabResource(dir, null);
    assertEquals(200, r.gazetteerBuild("wdpa").getStatus());
    assertThrows(NotFoundException.class, () -> r.gazetteerOverview("wdpa"));
    assertThrows(NotFoundException.class, () -> r.gazetteerMap("wdpa"));
    assertThrows(NotFoundException.class, () -> r.gazetteerBuild("text"));
    assertThrows(NotFoundException.class, () -> r.gazetteerLabels("nope"));
    assertThrows(NotFoundException.class, () -> r.gazetteerLabels("../wdpa"));
    assertThrows(NotFoundException.class, () -> new VocabResource((File) null, null).gazetteerBuild("wdpa"));
  }

  private VocabResource resourceWithLabels() throws Exception {
    File dir = tmp.newFolder();
    Files.createDirectories(dir.toPath().resolve("tdwg"));
    Files.writeString(dir.toPath().resolve("tdwg/labels.tsv"), "1\tEurope\n");
    Files.createDirectories(dir.toPath().resolve("iso"));
    Files.writeString(dir.toPath().resolve("iso/labels.tsv"), "DE\tGermany\nDE-BE\tBerlin\nYU\tYugoslavia\n");
    Files.createDirectories(dir.toPath().resolve("realm"));
    Files.writeString(dir.toPath().resolve("realm/labels.tsv"), "Neotropic\tNeotropic\nTropicalAtlantic\tTropical Atlantic\n");
    return new VocabResource(dir, new AreaLabelLookup(dir));
  }

  @Test
  public void areaKnown() throws Exception {
    var r = resourceWithLabels();
    assertTrue(r.area("tdwg", "1").isPresent());
    assertTrue(r.area("iso", "DE").isPresent());
    assertTrue(r.area("iso", "DE-BE").isPresent());
    assertTrue(r.area("realm", "Neotropic").isPresent());
    assertTrue(r.area("realm", "TropicalAtlantic").isPresent());
    // bundled countries do not need a label file
    assertTrue(r.area("iso", "XZ").isPresent());
    // no labels means we cannot tell
    assertTrue(r.area("iho", "999").isPresent());
    assertTrue(r.area("text", "somewhere").isPresent());
  }

  @Test
  public void areaUnknownIs404() throws Exception {
    var r = resourceWithLabels();
    // an empty optional is a 404
    assertTrue(r.area("tdwg", "99").isEmpty());
    assertTrue(r.area("iso", "DE-XX").isEmpty());
    assertThrows(NotFoundException.class, () -> r.area("iso", "ZZ"));
    assertThrows(NotFoundException.class, () -> r.area("foo", "1"));
    assertThrows(NotFoundException.class, () -> r.area("teow", "PA0445"));
    assertTrue(r.area("realm", "shipwreck").isEmpty());
  }

  @Test
  public void areaListIsoIncludesSubdivisions() throws Exception {
    var withoutLabels = new VocabResource((File) null, null).areas(Gazetteer.ISO);
    assertEquals(Country.values().length, withoutLabels.size());

    var areas = resourceWithLabels().areas(Gazetteer.ISO);
    // labels of a bundled country are not listed twice
    assertEquals(Country.values().length + 2, areas.size());
    assertEquals(List.of(new GenericArea(Gazetteer.ISO, "DE-BE", "Berlin"), new GenericArea(Gazetteer.ISO, "YU", "Yugoslavia")),
      areas.stream().filter(a -> a instanceof GenericArea).toList());
  }

  @Test
  public void areaListRealm() throws Exception {
    assertEquals(List.of(new GenericArea(Gazetteer.REALM, "Neotropic", "Neotropic"), new GenericArea(Gazetteer.REALM, "TropicalAtlantic", "Tropical Atlantic")),
      resourceWithLabels().areas(Gazetteer.REALM));
    assertThrows(NotFoundException.class, () -> new VocabResource((File) null, null).areas(Gazetteer.REALM));
  }

  @Test
  public void listedVocabsResolve() throws Exception {
    var r = new VocabResource((File) null, null);
    assertTrue(r.list().contains("term"));
    assertFalse(r.list().contains("terms"));
    for (String vocab : r.list()) {
      // language, geotime and term have their own methods
      if (!List.of("language", "geotime", "term").contains(vocab)) {
        assertFalse(vocab, r.values(vocab).isEmpty());
      }
    }
  }

  @Test
  public void taxGroupIcons() throws Exception {
    var r = new VocabResource((File) null, null);
    try (Response resp = r.taxGroupIcon("eukaryotes")) {
      assertEquals(200, resp.getStatus());
      assertEquals("image/svg+xml", resp.getMediaType().toString());
      assertTrue(new String(writeEntity(resp)).startsWith("<svg "));
    }
    try (Response resp = r.taxGroupIconPng("OtherFungi", 128)) {
      assertEquals("image/png", resp.getMediaType().toString());
      var png = writeEntity(resp);
      assertEquals((byte) 0x89, png[0]);
      assertEquals("PNG", new String(png, 1, 3));
    }
    assertThrows(NotFoundException.class, () -> r.taxGroupIcon("Dinosaurs"));
    assertThrows(NotFoundException.class, () -> r.taxGroupIconPng("Plants", 20));
  }

  @Test
  public void singleValue() {
    var r = new VocabResource((File) null, null);
    var chordates = r.value("taxgroup", "Chordates").orElseThrow();
    assertEquals("chordates", chordates.get("name"));
    assertTrue(r.value("taxgroup", "Dinosaurs").isEmpty());
  }

  @Test
  public void taxGroupVocabListsIconTaxa() throws Exception {
    var r = new VocabResource((File) null, null);
    var chordates = r.values("taxgroup").stream().filter(m -> "chordates".equals(m.get("name"))).findFirst().orElseThrow();
    assertEquals(java.util.List.of("Salmo trutta", "Turdus merula", "Rana temporaria"), chordates.get("iconTaxa"));
    assertEquals("https://api.checklistbank.org/vocab/taxgroup/Chordates/icon.svg", chordates.get("iconSVG").toString());
  }

  @Test
  public void normalizeFilenameMatchesBuildScripts() {
    assertEquals("37-4-1", VocabResource.normalizeFilename("37/4/1"));
    // case is preserved
    assertEquals("MRGID", VocabResource.normalizeFilename("MRGID"));
    assertEquals("28A", VocabResource.normalizeFilename("28A"));
    assertEquals("28a", VocabResource.normalizeFilename("28a"));
    assertEquals("A-B", VocabResource.normalizeFilename("A:B"));
    // whitespace runs collapse to a single dash, outer whitespace is stripped
    assertEquals("Baltic-Sea", VocabResource.normalizeFilename(" Baltic\tSea "));
    // backslash and runs of mixed separators collapse to a single dash
    assertEquals("foo-bar", VocabResource.normalizeFilename("foo\\bar"));
    assertEquals("foo-bar", VocabResource.normalizeFilename("foo//bar"));
    assertEquals("foo-bar", VocabResource.normalizeFilename("foo / :bar"));
    // leading/trailing dashes (from boundary separators) are trimmed
    assertEquals("foo", VocabResource.normalizeFilename(" /foo/ "));
  }

  private static byte[] writeEntity(Response resp) throws Exception {
    var buf = new java.io.ByteArrayOutputStream();
    ((jakarta.ws.rs.core.StreamingOutput) resp.getEntity()).write(buf);
    return buf.toByteArray();
  }
}
