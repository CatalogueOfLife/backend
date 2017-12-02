package org.col.parser;

import org.apache.commons.lang3.StringUtils;
import org.col.api.Name;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.api.UnparsableNameException;

import java.util.Optional;

/**
 * Wrapper around the GBIF Name parser to deal with col Name and API.
 */
public class NameParser implements Parser<Name> {
  public static final NameParser PARSER = new NameParser();
  private static final NameParserGBIF PARSER_INTERNAL = new NameParserGBIF();

  public Optional<Name> parse(String name) {
    return parse(name, Rank.UNRANKED);
  }

  /**
   * Fully parses a name using #parse(String, Rank) but converts names that throw a UnparsableException
   * into ParsedName objects with the scientific name, rank and name type given.
   */
  public Optional<Name> parse(String name, Rank rank) {
    if (StringUtils.isBlank(name)) {
      return Optional.empty();
    }

    Name n;
    try {
      n = new Name(PARSER_INTERNAL.parse(name, rank));
      n.setScientificName(n.canonicalNameWithoutAuthorship());
      if (!n.isParsed() || !n.isAuthorsParsed()) {
        n.addIssue(Issue.UNPARSABLE_NAME);
      }

    } catch (UnparsableNameException e) {
      n = new Name();
      n.setParsed(false);
      n.setAuthorsParsed(false);
      n.setType(e.getType());
      n.setRank(rank);
      n.setScientificName(e.getName());
      // adds an issue in case the type indicates a parsable name
      if (n.getType().isParsable()) {
        n.addIssue(Issue.UNPARSABLE_NAME);
      }
    }
    return Optional.of(n);
  }

}
