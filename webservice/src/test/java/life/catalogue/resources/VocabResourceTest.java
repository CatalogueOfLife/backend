package life.catalogue.resources;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

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
