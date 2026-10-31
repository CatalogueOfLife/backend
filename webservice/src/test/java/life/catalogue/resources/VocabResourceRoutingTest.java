package life.catalogue.resources;

import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.SuffixAcceptRequestFilter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Requests real urls, as SuffixAcceptRequestFilter strips a known suffix before jersey matches a route
 * and calling the resource methods directly would not notice a route that can never match.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class VocabResourceRoutingTest {

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, (byte) 0xff};
  private static final Path DIR = gazetteers();

  static final ResourceExtension EXT = ResourceExtension.builder()
    .addResource(new VocabResource(DIR.toFile(), null))
    .addProvider(SuffixAcceptRequestFilter.class)
    .build();

  private static Path gazetteers() {
    try {
      Path dir = Files.createTempDirectory("gazetteers");
      Files.createDirectories(dir.resolve("realm"));
      Files.write(dir.resolve("realm/map-thumb.png"), PNG);
      // has build info, but no shapes and therefore no map
      Files.createDirectories(dir.resolve("wdpa"));
      Files.writeString(dir.resolve("wdpa/build.json"), "{}");
      return dir;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @AfterAll
  static void cleanup() throws IOException {
    FileUtils.deleteDirectory(new File(DIR.toString()));
  }

  private Response get(String gazetteer) {
    return EXT.target("/vocab/gazetteer/" + gazetteer + "/map-thumb.png").request().get();
  }

  @Test
  public void mapThumbIsServedAsPng() {
    try (Response resp = get("realm")) {
      assertEquals(200, resp.getStatus());
      assertEquals(MoreMediaTypes.IMG_PNG, resp.getMediaType().toString());
      assertArrayEquals(PNG, resp.readEntity(byte[].class));
    }
  }

  @Test
  public void mapThumbMissingIs404() {
    try (Response resp = get("wdpa")) {
      assertEquals(404, resp.getStatus());
    }
    // a known gazetteer without even a directory, and an unknown one
    try (Response resp = get("text")) {
      assertEquals(404, resp.getStatus());
    }
    try (Response resp = get("nope")) {
      assertEquals(404, resp.getStatus());
    }
  }
}
