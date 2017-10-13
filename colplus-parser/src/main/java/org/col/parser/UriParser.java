package org.col.parser;

import org.gbif.common.parsers.UrlParser;

import java.net.URI;
import java.util.Optional;

/**
 * URI parser as wrapper around the gbif url parser
 */
public class UriParser implements Parser<URI> {

  @Override
  public Optional<URI> parse(String value) {
    return Optional.ofNullable(UrlParser.parse(value));
  }
}
