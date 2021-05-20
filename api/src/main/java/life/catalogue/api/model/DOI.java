package life.catalogue.api.model;


import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import life.catalogue.common.id.IdConverter;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

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

  private static final String DATASET_PATH = "d";
  private static final String EXPORT_PATH = "e";
  private static final String OTHER_PATH = "x";
  private static final Pattern DATASET_PATTERN = Pattern.compile("^"+DATASET_PATH+"([^-]+)$");
  private static final Pattern SOURCE_DATASET_PATTERN = Pattern.compile("^"+DATASET_PATH+"(.+)-(.+)$");

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

  public static DOI dataset(String prefix, int datasetKey) {
    String suffix = DATASET_PATH + IdConverter.LATIN29.encode(datasetKey);
    return new DOI(prefix, suffix);
  }

  public static DOI datasetSource(String prefix, int datasetKey, int sourceKey) {
    String suffix = DATASET_PATH + IdConverter.LATIN29.encode(datasetKey) + "-" + IdConverter.LATIN29.encode(sourceKey);
    return new DOI(prefix, suffix);
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

  @JsonIgnore
  public boolean isCOL() {
    return COL_PREFIX.equalsIgnoreCase(prefix) || TEST_PREFIX.equalsIgnoreCase(prefix);
  }

  @JsonIgnore
  public boolean isGBIF() {
    return GBIF_PREFIX.equalsIgnoreCase(prefix);
  }

  @JsonIgnore
  public int datasetKey() throws IllegalArgumentException {
    Preconditions.checkArgument(isCOL(), "COL DOI required");
    Matcher m = DATASET_PATTERN.matcher(suffix);
    if (m.find()) {
      return IdConverter.LATIN29.decode(m.group(1));
    }
    throw new IllegalArgumentException("Not a valid COL dataset DOI: " + getDoiName());
  }

  @JsonIgnore
  public DSID<Integer> sourceDatasetKey() throws IllegalArgumentException {
    Preconditions.checkArgument(isCOL(), "COL DOI required");
    Matcher m = SOURCE_DATASET_PATTERN.matcher(suffix);
    if (m.find()) {
      return DSID.of(IdConverter.LATIN29.decode(m.group(1)), IdConverter.LATIN29.decode(m.group(2)));
    }
    throw new IllegalArgumentException("Not a valid COL source dataset DOI: " + getDoiName());
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

}
