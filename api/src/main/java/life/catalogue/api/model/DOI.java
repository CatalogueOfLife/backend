package life.catalogue.api.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonSerialize(
  using = DOI.DoiSerializer.class
)
@JsonDeserialize(
  using = DOI.DoiDeserializer.class
)
public class DOI implements Serializable {
  private static final Logger LOG = LoggerFactory.getLogger(DOI.class);
  private static final String CHAR_ENCODING = "UTF-8";
  public static final String GBIF_PREFIX = "10.15468";
  public static final String COL_PREFIX = "10.48580";
  public static final String TEST_PREFIX = "10.80631";
  private static final Pattern HTTP = Pattern.compile("^https?://(dx\\.)?doi\\.org/(urn:)?(doi:)?", 2);
  private static final Pattern PARSER = Pattern.compile("^(?:urn:)?(?:doi:)?(10(?:\\.[0-9]+)+)/(.+)$", 2);
  private static final String RESOLVER = "https://doi.org/";
  private static final String SCHEME = "doi:";
  private String prefix;
  private String suffix;

  public static boolean isParsable(String source) {
    if (StringUtils.isNotEmpty(source)) {
      try {
        return PARSER.matcher(decodeUrl(source)).find();
      } catch (IllegalArgumentException var2) {
        LOG.debug("Can not decode URL from the following DOI: {}", source);
      }
    }

    return false;
  }

  public static DOI col(String suffix) {
    return new DOI(COL_PREFIX, suffix);
  }

  public static DOI test(String suffix) {
    return new DOI(TEST_PREFIX, suffix);
  }

  public DOI() {
  }

  public DOI(String doi) {
    Objects.requireNonNull(doi, "DOI required");
    Matcher m = PARSER.matcher(decodeUrl(doi));
    if (m.find()) {
      this.prefix = m.group(1).toLowerCase();
      this.suffix = m.group(2).toLowerCase();
    } else {
      throw new IllegalArgumentException(doi + " is not a valid DOI");
    }
  }

  public DOI(@NotNull String prefix, @Nullable String suffix) {
    this.prefix = Objects.requireNonNull(prefix, "DOI prefix required").toLowerCase();
    Preconditions.checkArgument(prefix.startsWith("10."));
    this.suffix = suffix == null ? null : suffix.toLowerCase();
  }

  private static String decodeUrl(@NotNull String doi) {
    Matcher m = HTTP.matcher(doi);
    if (m.find()) {
      doi = m.replaceFirst("");

      try {
        return URI.create(URLEncoder.encode(doi, CHAR_ENCODING)).getPath();
      } catch (UnsupportedEncodingException var3) {
        throw new IllegalArgumentException("Unsupported DOI encoding", var3);
      }
    } else {
      return doi;
    }
  }

  public String getPrefix() {
    return this.prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String getSuffix() {
    return this.suffix;
  }

  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  public URI getUrl() {
    try {
      return URI.create(RESOLVER + this.prefix + '/' + URLEncoder.encode(this.suffix, CHAR_ENCODING));
    } catch (UnsupportedEncodingException var2) {
      throw new IllegalStateException("Unsupported DOI encoding", var2);
    }
  }

  public String getDoiString() {
    return "doi:" + this.getDoiName();
  }

  public String getDoiName() {
    return this.prefix + '/' + this.suffix;
  }

  public String toString() {
    return this.getDoiName();
  }

  @JsonIgnore
  public boolean isComplete() {
    return prefix != null && suffix != null;
  }

  public int hashCode() {
    return Objects.hash(new Object[]{this.prefix, this.suffix});
  }

  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj != null && this.getClass() == obj.getClass()) {
      DOI other = (DOI)obj;
      return Objects.equals(this.prefix, other.prefix) && Objects.equals(this.suffix, other.suffix);
    } else {
      return false;
    }
  }

  public static class DoiDeserializer extends JsonDeserializer<DOI> {
    public DoiDeserializer() {
    }

    public DOI deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return p != null && p.getTextLength() > 0 ? new DOI(p.getText()) : null;
    }
  }

  public static class DoiSerializer extends JsonSerializer<DOI> {
    public DoiSerializer() {
    }

    public void serialize(DOI value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(value.toString());
    }
  }
}
