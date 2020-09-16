package life.catalogue.db.mapper.legacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * The legacy API always returns http 200 OK responses,
 * but uses an error message as content instead.
 */
public class LError extends LResponse {
  private final String error;

  public LError(String id, String name, String message, String version) {
    super(id, name, 0, version);
    this.error = message;
  }

  public LError(String id, String name, int start, String message, String version) {
    super(id, name, start, version);
    this.error = message;
  }

  @JsonProperty("error_message")
  @JacksonXmlProperty(isAttribute = true, localName = "error_message")
  public String getError() {
    return error;
  }
}
