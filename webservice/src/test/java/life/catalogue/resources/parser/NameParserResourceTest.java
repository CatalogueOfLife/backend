package life.catalogue.resources.parser;

import life.catalogue.api.jackson.ApiModule;

import org.gbif.nameparser.api.ParseResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.Assert.*;

public class NameParserResourceTest {
  final NameParserResource resource = new NameParserResource();

  static InputStream text(String lines) {
    return new ByteArrayInputStream(lines.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void plainTextSkipsBlankLines() {
    var names = resource.parsePlainText(null, null, text("Abies alba\n\n  \nPicea abies\n"));
    assertEquals(2, names.size());
    assertEquals("Abies alba", names.get(0).getScientificName());
    assertEquals("Picea abies", names.get(1).getScientificName());
  }

  @Test
  public void nativePlainTextSkipsBlankLines() {
    List<ParseResult> results = resource.parsePlainTextNative(null, null, text("Abies alba\n\nPicea abies\n"));
    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(r -> r instanceof ParseResult.Parsed));
  }

  @Test(expected = IllegalArgumentException.class)
  public void nativeRequiresName() {
    resource.parseNative(null, null, null, null);
  }

  @Test
  public void nativeJsonNamesItsResult() throws Exception {
    assertResult("parsed", "Abies alba Mill.");
    assertResult("informal", "Rhizobium sp. RMCC TR1811");
    assertResult("unparsable", "BOLD:AAA1234");
  }

  void assertResult(String expected, String name) throws Exception {
    ParseResult result = resource.parseNative(null, null, name, null);
    JsonNode json = ApiModule.MAPPER.valueToTree(result);
    assertEquals(name, expected, json.path("result").asText());
    assertEquals(name, result.canonicalNameComplete(), json.path("label").asText());
  }

  @Test
  public void pnIssueSerializesUsageProperties() throws Exception {
    var pn = resource.parseGet(null, null, "Abies alba Mill.", null, null).orElseThrow();
    JsonNode json = ApiModule.MAPPER.valueToTree(pn);
    assertTrue(json.has("extinct"));
    assertEquals("Abies alba", json.path("scientificName").asText());
  }
}
