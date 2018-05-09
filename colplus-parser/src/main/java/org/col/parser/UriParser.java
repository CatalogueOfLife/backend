package org.col.parser;

import java.net.URI;
import java.util.Optional;

import org.gbif.common.parsers.UrlParser;

/**
 * URI parser as wrapper around the gbif url parser
 */
public class UriParser implements Parser<URI> {
  public static final UriParser PARSER = new UriParser();

  @Override
  public Optional<URI> parse(String value) {
    return Optional.ofNullable(UrlParser.parse(value));
  }
}
