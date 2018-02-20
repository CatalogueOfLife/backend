package org.col.parser;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Name;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.Warnings;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.api.UnparsableNameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Wrapper around the GBIF Name parser to deal with col Name and API.
 */
public class NameParser implements Parser<Name> {
  private static Logger LOG = LoggerFactory.getLogger(NameParser.class);
  public static final NameParser PARSER = new NameParser();
  private static final NameParserGBIF PARSER_INTERNAL = new NameParserGBIF();
  private static final Map<String, Issue> WARN_TO_ISSUE = ImmutableMap.<String, Issue>builder()
      .put(Warnings.NULL_EPITHET, Issue.NULL_EPITHET)
      .put(Warnings.UNUSUAL_CHARACTERS, Issue.UNUSUAL_CHARACTERS)
      .put(Warnings.SUBSPECIES_ASSIGNED, Issue.SUBSPECIES_ASSIGNED)
      .put(Warnings.LC_MONOMIAL, Issue.LC_MONOMIAL)
      .put(Warnings.INDET_CULTIVAR, Issue.INDET_CULTIVAR)
      .put(Warnings.INDET_SPECIES, Issue.INDET_SPECIES)
      .put(Warnings.INDET_INFRASPECIES, Issue.INDET_INFRASPECIES)
      .put(Warnings.HIGHER_RANK_BINOMIAL, Issue.HIGHER_RANK_BINOMIAL)
      .put(Warnings.QUESTION_MARKS_REMOVED, Issue.QUESTION_MARKS_REMOVED)
      .put(Warnings.REPL_ENCLOSING_QUOTE, Issue.REPL_ENCLOSING_QUOTE)
      .put(Warnings.MISSING_GENUS, Issue.MISSING_GENUS)
      .put(Warnings.HTML_ENTITIES, Issue.HTML_ENTITIES)
      .put(Warnings.XML_ENTITIES, Issue.XML_ENTITIES)
      .build();

  public Optional<Name> parse(String name) {
    return parse(name, Rank.UNRANKED);
  }

  /**
   * @return a name instance with just the parsed authorship, i.e. combination & original year & author list
   */
  public Optional<ParsedName> parseAuthorship(String authorship) {
    try {
      return Optional.of(PARSER_INTERNAL.parse("Abies alba "+authorship, Rank.SPECIES));

    } catch (UnparsableNameException e) {
      return Optional.empty();
    }
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
      n = fromParsedName(PARSER_INTERNAL.parse(name, rank));
      n.updateScientificName();

    } catch (UnparsableNameException e) {
      n = new Name();
      n.setRank(rank);
      n.setScientificName(e.getName());
      n.setType(e.getType());
      // adds an issue in case the type indicates a parsable name
      if (n.getType().isParsable()) {
        n.addIssue(Issue.UNPARSABLE_NAME);
      }
    }
    return Optional.of(n);
  }

  private static Name fromParsedName(ParsedName pn) {
    Name n = new Name();
    n.setUninomial(pn.getUninomial());
    n.setGenus(pn.getGenus());
    n.setInfragenericEpithet(pn.getInfragenericEpithet());
    n.setSpecificEpithet(pn.getSpecificEpithet());
    n.setInfraspecificEpithet(pn.getInfraspecificEpithet());
    n.setCultivarEpithet(pn.getCultivarEpithet());
    n.setStrain(pn.getStrain());
    n.setCombinationAuthorship(pn.getCombinationAuthorship());
    n.setBasionymAuthorship(pn.getBasionymAuthorship());
    n.setSanctioningAuthor(pn.getSanctioningAuthor());
    n.setRank(pn.getRank());
    n.setCode(pn.getCode());
    n.setCandidatus(pn.isCandidatus());
    n.setNotho(pn.getNotho());
    n.setRemarks(pn.getRemarks());
    n.setType(pn.getType());
    // issues
    if (!pn.getState().isParsed()) {
      n.addIssue(Issue.UNPARSABLE_NAME);
    }
    if (pn.isDoubtful()) {
      n.addIssue(Issue.DOUBTFUL_NAME);
    }
    // translate warnings into issues
    for (String warn : pn.getWarnings()) {
      if (WARN_TO_ISSUE.containsKey(warn)) {
        n.addIssue(WARN_TO_ISSUE.get(warn));
      } else {
        LOG.debug("Unknown parser warning: {}", warn);
      }
    }
    //ignore taxonomic note
    //TODO: try to convert nom notes to enumeration. Add to remarks for now
    n.addRemark(pn.getNomenclaturalNotes());
    return n;
  }

}
