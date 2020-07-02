package life.catalogue.parser;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.common.tax.NameFormatter;
import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.ParserConfigs;
import org.gbif.nameparser.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper around the GBIF Name parser to deal with the col Name class and API.
 */
public class NameParser implements Parser<ParsedNameUsage>, AutoCloseable {
  private static Logger LOG = LoggerFactory.getLogger(NameParser.class);
  public static final NameParser PARSER = new NameParser();
  private static final NameParserGBIF PARSER_INTERNAL = new NameParserGBIF();

  private static final Pattern NORM_PUNCT_WS = Pattern.compile("\\s*([)}\\],;:])\\s*");
  private static final Pattern NORM_WS_PUNCT = Pattern.compile("\\s*([({\\[])\\s*");
  private static final Pattern NORM_AND = Pattern.compile("\\s*(\\b(?:and|et|und)\\b|(?:,\\s*)?&)\\s*");
  private static final Pattern NORM_ET_AL = Pattern.compile("(&|et) al\\.?(?:[^.]|$)");
  private static final Pattern NORM_ANON = Pattern.compile("\\b(anon\\.?)(\\b|\\s|$)");
  private static final String YEAR = "[12][0-9][0-9][0-9?]";
  private static final Pattern COMMA_BEFORE_YEAR = Pattern.compile("(?<!,)\\s+("+YEAR+")");
  private static final Pattern COMMA_AT_END = Pattern.compile("\\s*[,;:]\\s*$");
  private static final Pattern NORM_WHITESPACE = Pattern.compile("(?:\\\\[nr]|\\s)+");

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

  public static ParserConfigs configs() {
    return PARSER_INTERNAL.getConfigs();
  }

  private Timer timer;
  
  /**
   * Optionally register timer metrics for name parsing events
   *
   * @param registry
   */
  public void register(MetricRegistry registry) {
    timer = registry.timer("life.catalogue.parser.name");
  }

  /**
   * @deprecated use parse(name, rank, code, issues) instead!
   */
  @Deprecated
  public Optional<ParsedNameUsage> parse(String name) {
    return parse(name, Rank.UNRANKED, null, IssueContainer.VOID);
  }
  
  /**
   * @return a name instance with just the parsed authorship, i.e. combination & original year & author list
   */
  public Optional<ParsedAuthorship> parseAuthorship(String authorship) {
    if (Strings.isNullOrEmpty(authorship)) return Optional.of(new ParsedName());
    try {
      ParsedAuthorship pa = PARSER_INTERNAL.parseAuthorship(authorship);
      if (pa.getState() == ParsedName.State.COMPLETE) {
        return Optional.of(pa);
      }
    } catch (UnparsableNameException e) {
    }
    return Optional.empty();
  }

  /**
   * Populates the parsed authorship of a given name instance by parsing a single authorship string.
   * Only parses the authorship if the name itself is already parsed.
   */
  public void parseAuthorshipIntoName(ParsedNameUsage pnu, final String authorship, IssueContainer v){
    // try to add an authorship if not yet there
    if (pnu.getName().isParsed() && !Strings.isNullOrEmpty(authorship)) {
      ParsedAuthorship pnAuthorship = parseAuthorship(authorship).orElseGet(() -> {
        LOG.info("Unparsable authorship {}", authorship);
        v.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
        // add the full, unparsed authorship in this case to not lose it
        ParsedName pn = new ParsedName();
        pn.getCombinationAuthorship().getAuthors().add(authorship);
        return pn;
      });

      // we might have already parsed an authorship from the scientificName string which does not match up?
      if (pnu.getName().hasAuthorship()) {
        String prevAuthorship = NameFormatter.authorship(pnu.getName());
        if (!prevAuthorship.equalsIgnoreCase(pnAuthorship.authorshipComplete())) {
          v.addIssue(Issue.INCONSISTENT_AUTHORSHIP);
          LOG.info("Different authorship found in name {} than in parsed version: [{}] vs [{}]",
              pnu.getName(), prevAuthorship, pnAuthorship.authorshipComplete());
        }
      }
      copyToPNU(pnAuthorship, pnu, v);
      // use original authorship string but normalize whitespace
      pnu.getName().setAuthorship( normalizeAuthorship(authorship, pnAuthorship.getTaxonomicNote()) );
    }
  }

  static String note2pattern(String x) {
    return x
      .replaceAll(" +", " *")
      .replace(".", "\\. *")
      .replace("-", " *\\- *")
      .replace("(", " *\\( *")
      .replace(")", " *\\) *")
      .replace("[", " *\\[ *")
      .replace("]", " *\\] *");
  }

  static String normalizeAuthorship(final String authorship, String taxNote) {
    String name = authorship;
    // we need to exclude the taxonomic bits from the authorship, otherwise we render them twice
    if (taxNote != null) {
      // this is more tricky than it sounds as we altered the taxNote and it may have more/less whitespace in particular
      Pattern noteP = Pattern.compile("^(.*)" + note2pattern(taxNote) + "(.*)$", Pattern.CASE_INSENSITIVE);
      Matcher m = noteP.matcher(authorship);
      if (m.find()) {
        name = m.replaceFirst("$1 $2");
        // remove final comma
        name = COMMA_AT_END.matcher(name).replaceFirst("");
      }
    }

    // normalise different usages of ampersand, and, et &amp; to always use &
    name = NORM_AND.matcher(name).replaceAll(" & ");
    name = NORM_ET_AL.matcher(name).replaceAll("et al.");

    // put a comma before any year
    Matcher m = COMMA_BEFORE_YEAR.matcher(name);
    if (m.find()) {
      name = m.replaceFirst(", $1");
    }

    // capitalize Anonumous author
    m = NORM_ANON.matcher(name);
    if (m.find()) {
      name = m.replaceFirst("Anon.");
    }

    name = NORM_WS_PUNCT.matcher(name).replaceAll(" $1");
    name = NORM_PUNCT_WS.matcher(name).replaceAll("$1 ");

    // finally whitespace and trimming
    name = NORM_WHITESPACE.matcher(name).replaceAll(" ");
    return StringUtils.trimToNull(name);
  }

  static <T> void setIfNull(T val, Supplier<T> getter, Consumer<T> setter) {
    if (val != null && getter.get() == null) {
      setter.accept(val);
    }
  }

  /**
   * Copies all authorship properties but the full authorship "cache"
   * @param pn
   * @param pnu
   * @param issues
   */
  private static void copyToPNU(ParsedAuthorship pn, ParsedNameUsage pnu, IssueContainer issues){
    pnu.getName().setCombinationAuthorship(pn.getCombinationAuthorship());
    pnu.getName().setSanctioningAuthor(pn.getSanctioningAuthor());
    pnu.getName().setBasionymAuthorship(pn.getBasionymAuthorship());
    // propagate notes and unparsed bits found in authorship if not already existing
    setIfNull(pn.getNomenclaturalNote(), pnu.getName()::getNomenclaturalNote, pnu.getName()::setNomenclaturalNote);
    setIfNull(pn.getPublishedIn(), pnu::getPublishedIn, pnu::setPublishedIn);
    setIfNull(pn.getTaxonomicNote(), pnu::getTaxonomicNote, pnu::setTaxonomicNote);
    if (pn.getUnparsed() != null) {
      pnu.getName().setUnparsed(pn.getUnparsed());
    }
    if (pn.isExtinct()) {
      pnu.setExtinct(pn.isExtinct());
    }
    if (pn.isManuscript()) {
      pnu.getName().setNomStatus(NomStatus.MANUSCRIPT);
    }

    // issues
    switch (pn.getState()) {
      case PARTIAL:
        issues.addIssue(Issue.PARTIALLY_PARSABLE_NAME);
        break;
      case NONE:
        issues.addIssue(Issue.UNPARSABLE_NAME);
        break;
    }

    if (pn.isDoubtful()) {
      issues.addIssue(Issue.DOUBTFUL_NAME);
    }
    // translate warnings into issues
    for (String warn : pn.getWarnings()) {
      if (WARN_TO_ISSUE.containsKey(warn)) {
        issues.addIssue(WARN_TO_ISSUE.get(warn));
      } else {
        LOG.debug("Unknown parser warning: {}", warn);
      }
    }
  }

  /**
   * Fully parses a name using #parse(String, Rank) but converts names that throw a UnparsableException
   * into ParsedName objects with the scientific name, rank and name type given.
   */
  public Optional<ParsedNameUsage> parse(String name, Rank rank, NomCode code, IssueContainer issues) {
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
  public Optional<ParsedNameUsage> parse(Name n, IssueContainer issues) {
    if (StringUtils.isBlank(n.getScientificName())) {
      return Optional.empty();
    }
    ParsedNameUsage pnu;
    Timer.Context ctx = timer == null ? null : timer.time();
    try {
      final String authorship = n.getAuthorship();
      pnu = fromParsedName(n, PARSER_INTERNAL.parse(n.getScientificName(), n.getRank(), n.getCode()), issues);
      // try to add an authorship if not yet there
      parseAuthorshipIntoName(pnu, authorship, issues);

    } catch (UnparsableNameException e) {
      pnu = new ParsedNameUsage();
      pnu.setName(n);
      pnu.getName().setRank(n.getRank());
      pnu.getName().setScientificName(e.getName());
      pnu.getName().setType(e.getType());
      // adds an issue in case the type indicates a parsable name
      if (pnu.getName().getType().isParsable()) {
        issues.addIssue(Issue.UNPARSABLE_NAME);
      }
    } finally {
      if (ctx != null) {
        ctx.stop();
      }
    }
    return Optional.of(pnu);
  }
  
  public Optional<NameType> determineType(Name name) {
    String sciname = name.getScientificName();
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
  private static ParsedNameUsage fromParsedName(Name n, ParsedName pn, IssueContainer issues) {
    // if we parsed strains we do not keep them in the Name class
    if (!StringUtils.isBlank(pn.getStrain())) {
      if (pn.getUnparsed() == null) {
        pn.setState(ParsedName.State.PARTIAL);
        pn.setUnparsed(pn.getStrain());
      }
    }

    ParsedNameUsage pnu = new ParsedNameUsage();
    pnu.setName(n);
    copyToPNU(pn, pnu, issues);
    // name specifics
    n.setUninomial(pn.getUninomial());
    n.setGenus(pn.getGenus());
    n.setInfragenericEpithet(pn.getInfragenericEpithet());
    n.setSpecificEpithet(pn.getSpecificEpithet());
    n.setInfraspecificEpithet(pn.getInfraspecificEpithet());
    n.setCultivarEpithet(pn.getCultivarEpithet());
    n.setRank(pn.getRank());
    n.setCode(pn.getCode());
    n.setCandidatus(pn.isCandidatus());
    n.setNotho(pn.getNotho());
    n.setType(pn.getType());

    if (pn.isIncomplete()) {
      issues.addIssue(Issue.INCONSISTENT_NAME);
    }

    // we rebuilt the caches as we dont have any original authorship yet - it all came in through the single scientificName
    n.rebuildScientificName();
    n.rebuildAuthorship();
    return pnu;
  }

  @Override
  public void close() throws Exception {
    if (PARSER_INTERNAL != null) {
      PARSER_INTERNAL.close();
    }
  }

}
