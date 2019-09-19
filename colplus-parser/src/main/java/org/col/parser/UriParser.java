package org.col.parser;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.gbif.common.parsers.UrlParser;

/**
 * URI parser as wrapper around the gbif url parser
 */
public class UriParser implements Parser<URI> {
  private final static Pattern WHITESPACE = Pattern.compile("\\s");
  public static final UriParser PARSER = new UriParser();

  @Override
  public Optional<URI> parse(String value) throws UnparsableException {
    // escape whitespace which is often given unescaped
    if (value != null) {
      value = WHITESPACE.matcher(value.trim()).replaceAll("%20");
    }
    URI uri = UrlParser.parse(value);
    // the GBIF parser never throws, if null check if we had data
    if (uri == null && !StringUtils.isBlank(value)) {
      throw new UnparsableException(URI.class, value);
    }
    return Optional.ofNullable(uri);
  }
}
