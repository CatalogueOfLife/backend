package org.col.parser;

import java.util.Map;
import java.util.Optional;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.col.api.model.IssueContainer;
import org.col.api.model.Name;
import org.col.api.model.NameAccordingTo;
import org.col.api.util.ObjectUtils;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around the GBIF Name parser to deal with col Name and API.
 */
public class NameParser implements Parser<NameAccordingTo> {
  private static Logger LOG = LoggerFactory.getLogger(NameParser.class);
  public static final NameParser PARSER = new NameParser();
  private static final NameParserGBIF PARSER_INTERNAL = new NameParserGBIF();
  private static final Map<String, Issue> WARN_TO_ISSUE = ImmutableMap.<String, Issue>builder()
      .put(Warnings.NULL_EPITHET, Issue.NULL_EPITHET)
      .put(Warnings.UNUSUAL_CHARACTERS, Issue.UNUSUAL_NAME_CHARACTERS)
      .put(Warnings.SUBSPECIES_ASSIGNED, Issue.SUBSPECIES_ASSIGNED)
      .put(Warnings.LC_MONOMIAL, Issue.LC_MONOMIAL)
      .put(Warnings.INDETERMINED, Issue.INDETERMINED)
      .put(Warnings.HIGHER_RANK_BINOMIAL, Issue.HIGHER_RANK_BINOMIAL)
      .put(Warnings.QUESTION_MARKS_REMOVED, Issue.QUESTION_MARKS_REMOVED)
      .put(Warnings.REPL_ENCLOSING_QUOTE, Issue.REPL_ENCLOSING_QUOTE)
      .put(Warnings.MISSING_GENUS, Issue.MISSING_GENUS)
      .put(Warnings.HTML_ENTITIES, Issue.ESCAPED_CHARACTERS)
      .put(Warnings.XML_TAGS, Issue.ESCAPED_CHARACTERS)
      .put(Warnings.BLACKLISTED_EPITHET, Issue.BLACKLISTED_EPITHET)
      .put(Warnings.NOMENCLATURAL_REFERENCE, Issue.CONTAINS_REFERENCE)
      .build();
  
  private Timer timer;
  
  /**
   * Optionally register timer metrics for name parsing events
   *
   * @param registry
   */
  public void register(MetricRegistry registry) {
    timer = registry.timer("org.col.parser.name");
  }
  
  /**
   * @deprecated use parse(name, rank, code, issues) instead!
   */
  @Deprecated
  public Optional<NameAccordingTo> parse(String name) {
    return parse(name, Rank.UNRANKED, null, IssueContainer.VOID);
  }
  
  /**
   * @return a name instance with just the parsed authorship, i.e. combination & original year & author list
   */
  public Optional<ParsedName> parseAuthorship(String authorship) {
    if (Strings.isNullOrEmpty(authorship)) return Optional.of(new ParsedName());
    try {
      ParsedName pn = PARSER_INTERNAL.parse("Abies alba " + authorship, Rank.SPECIES, null);
      if (pn.getState() == ParsedName.State.COMPLETE) {
        return Optional.of(pn);
      }
    } catch (UnparsableNameException e) {
    }
    return Optional.empty();
  }
  
  /**
   * Populates the parsed authorship of a given name instance by parsing a single authorship string.
   * Only parses the authorship if the name itself is already parsed.
   */
  public void parseAuthorshipIntoName(NameAccordingTo nat, String authorship, IssueContainer v){
    // try to add an authorship if not yet there
    if (nat.getName().isParsed() && !Strings.isNullOrEmpty(authorship)) {
      ParsedName pnAuthorship = parseAuthorship(authorship).orElseGet(() -> {
        LOG.warn("Unparsable authorship {}", authorship);
        v.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
        // add the full, unparsed authorship in this case to not lose it
        ParsedName pn = new ParsedName();
        pn.getCombinationAuthorship().getAuthors().add(authorship);
        return pn;
      });
    
      // we might have already parsed an authorship from the scientificName string which does not match up?
      if (nat.getName().hasAuthorship() &&
          !nat.getName().authorshipComplete().equalsIgnoreCase(pnAuthorship.authorshipComplete())) {
        v.addIssue(Issue.INCONSISTENT_AUTHORSHIP);
        LOG.info("Different authorship found in name {} than in parsed version: [{}] vs [{}]",
            nat.getName(), nat.getName().authorshipComplete(), pnAuthorship.authorshipComplete());
      }
      nat.getName().setCombinationAuthorship(pnAuthorship.getCombinationAuthorship());
      nat.getName().setSanctioningAuthor(pnAuthorship.getSanctioningAuthor());
      nat.getName().setBasionymAuthorship(pnAuthorship.getBasionymAuthorship());
      // propagate notes and unparsed bits found in authorship
      nat.getName().addRemark(pnAuthorship.getNomenclaturalNotes());
      if (pnAuthorship.getUnparsed() != null) {
        nat.getName().setAppendedPhrase(pnAuthorship.getUnparsed());
      }
      nat.addAccordingTo(pnAuthorship.getTaxonomicNote());
    }
  }
  
  /**
   * Fully parses a name using #parse(String, Rank) but converts names that throw a UnparsableException
   * into ParsedName objects with the scientific name, rank and name type given.
   */
  public Optional<NameAccordingTo> parse(String name, Rank rank, NomCode code, IssueContainer issues) {
    Name n = new Name();
    n.setScientificName(name);
    n.setRank(rank);
    n.setCode(code);
    return parse(n, issues);
  }

  /**
   * Fully parses a name using #parse(String, Rank) but converts names that throw a UnparsableException
   * into ParsedName objects with the scientific name, rank and name type given.
   *
   * Populates a given name instance with the parsing results.
   */
  public Optional<NameAccordingTo> parse(Name n, IssueContainer issues) {
    if (StringUtils.isBlank(n.getScientificName())) {
      return Optional.empty();
    }
    
    NameAccordingTo nat;
    Timer.Context ctx = timer == null ? null : timer.time();
    try {
      nat = fromParsedName(n, PARSER_INTERNAL.parse(n.getScientificName(), n.getRank(), n.getCode()), issues);
      nat.getName().updateNameCache();
      
    } catch (UnparsableNameException e) {
      nat = new NameAccordingTo();
      nat.setName(n);
      nat.getName().setRank(n.getRank());
      nat.getName().setScientificName(e.getName());
      nat.getName().setType(e.getType());
      // adds an issue in case the type indicates a parsable name
      if (nat.getName().getType().isParsable()) {
        issues.addIssue(Issue.UNPARSABLE_NAME);
      }
    } finally {
      if (ctx != null) {
        ctx.stop();
      }
    }
    return Optional.of(nat);
  }
  
  public Optional<NameType> determineType(Name name) {
    String sciname = name.canonicalNameComplete();
    if (StringUtils.isBlank(sciname)) {
      return Optional.of(NameType.NO_NAME);
    }
    try {
      ParsedName pn = PARSER_INTERNAL.parse(sciname, name.getRank(), name.getCode());
      return Optional.of(ObjectUtils.coalesce(pn.getType(), NameType.SCIENTIFIC));
    
    } catch (UnparsableNameException e) {
      return Optional.of(ObjectUtils.coalesce(e.getType(), NameType.SCIENTIFIC));
    }
  }
  
  /**
   * Uses an existing name instance to populate from a ParsedName instance
   */
  private static NameAccordingTo fromParsedName(Name n, ParsedName pn, IssueContainer issues) {
    n.setUninomial(pn.getUninomial());
    n.setGenus(pn.getGenus());
    n.setInfragenericEpithet(pn.getInfragenericEpithet());
    n.setSpecificEpithet(pn.getSpecificEpithet());
    n.setInfraspecificEpithet(pn.getInfraspecificEpithet());
    n.setCultivarEpithet(pn.getCultivarEpithet());
    n.setAppendedPhrase(pn.getStrain());
    n.setCombinationAuthorship(pn.getCombinationAuthorship());
    n.setBasionymAuthorship(pn.getBasionymAuthorship());
    n.setSanctioningAuthor(pn.getSanctioningAuthor());
    n.setRank(pn.getRank());
    n.setCode(pn.getCode());
    n.setCandidatus(pn.isCandidatus());
    n.setNotho(pn.getNotho());
    n.setType(pn.getType());
    // issues
    switch (pn.getState()) {
      case PARTIAL:
        issues.addIssue(Issue.PARTIALLY_PARSABLE_NAME);
        break;
      case NONE:
        issues.addIssue(Issue.UNPARSABLE_NAME);
        break;
      case COMPLETE:
        break;
    }
    if (pn.isDoubtful()) {
      issues.addIssue(Issue.DOUBTFUL_NAME);
    }
    if (pn.isIncomplete()) {
      issues.addIssue(Issue.INCONSISTENT_NAME);
    }
    // translate warnings into issues
    for (String warn : pn.getWarnings()) {
      if (WARN_TO_ISSUE.containsKey(warn)) {
        issues.addIssue(WARN_TO_ISSUE.get(warn));
      } else {
        LOG.debug("Unknown parser warning: {}", warn);
      }
    }
    if (pn.isManuscript()) {
      n.setNomStatus(NomStatus.MANUSCRIPT);
    }
    //TODO: try to convert nom notes to enumeration. Only add to remarks for now
    // can be sth like: nom.illeg., in Döring et all  reference
    n.setRemarks(pn.getNomenclaturalNotes());
    
    NameAccordingTo nat = new NameAccordingTo();
    nat.setName(n);
    nat.setAccordingTo(pn.getTaxonomicNote());
    
    return nat;
  }
  
}
