package org.col.parser;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Greedy URL parser assuming HTTP URIs in case no schema was given.
 * Modified version of the registry-metadata GreedyUriConverter.
 */
public class UriParser implements Parser<URI> {
  private static final Logger LOG = LoggerFactory.getLogger(UriParser.class);
  private static final String[] MULTI_VALUE_DELIMITERS = {"|#DELIMITER#|", "|", ",", ";"};
  private static final String HTTP_SCHEME = "http://";
  private final static Pattern WHITESPACE = Pattern.compile("\\s");
  public static final UriParser PARSER = new UriParser();
  
  // Pattern for things that are probably domains followed by a slash, without a protocol.
  // Doesn't match IDNs etc, but this is just for people who forgot the http:// anyway.
  // The longest TLD currently in existence is 24 characters long, but can be up to 63 according to specs.
  private static final Pattern DOMAIN_ISH = Pattern.compile("^[A-Za-z0-9.-]{1,60}\\.[A-Za-z]{2,10}(?:/.*)?");
  
  
  @Override
  public Optional<URI> parse(String value) throws UnparsableException {
    URI uri = UriParser.parseDontThrow(value);
    // the parsing below never throws, if null check if we had data
    if (uri == null && !StringUtils.isBlank(value)) {
      throw new UnparsableException(URI.class, value);
    }
    return Optional.ofNullable(uri);
  }
  
  /**
   * Convert a String into a java.net.URI.
   * In case its missing the protocol prefix, it is prefixed with the default protocol.
   *
   * @param value The input value to be converted
   *
   * @return The converted value, or null if not parsable or exception occurred
   */
  public static URI parseDontThrow(String value) {
    value = CharMatcher.whitespace().trimFrom(Strings.nullToEmpty(value));
    if (Strings.isNullOrEmpty(value)) {
      return null;
    }
  
    // escape whitespace which is often given unescaped
    value = WHITESPACE.matcher(value.trim()).replaceAll("%20");
  
    URI uri = null;
    try {
      uri = URI.create(value);
      if (!uri.isAbsolute() && DOMAIN_ISH.matcher(value).matches()) {
        // make into an HTTP address
        try {
          uri = URI.create(HTTP_SCHEME + value);
        } catch (IllegalArgumentException e) {
          // keep the previous scheme-less result
        }
      }
      
      // verify that we have a domain
      if (Strings.isNullOrEmpty(uri.getHost())) {
        return null;
      }
      
    } catch (IllegalArgumentException e) {
    }
    
    return uri;
  }
  
}
