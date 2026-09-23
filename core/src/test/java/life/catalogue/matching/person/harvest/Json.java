package life.catalogue.matching.person.harvest;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * JSON as the authorities answer it.
 */
final class Json {
  // Wikidata labels occasionally hold raw control characters that strict JSON rejects
  static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();

  private Json() {
  }

  /**
   * @return true for a whole JSON object that is no error. The query service sometimes answers 200 with a body cut
   *         off when it times out, and the Wikidata API answers 200 with an error when its databases lag.
   */
  static boolean complete(String body) {
    try {
      JsonNode n = MAPPER.readTree(body);
      return n != null && n.isObject() && !n.has("error");
    } catch (Exception e) {
      return false;
    }
  }
}
