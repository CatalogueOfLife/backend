package life.catalogue.parser;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.*;
import org.gbif.nameparser.rust.NameParserRust;

import javax.annotation.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/**
 * Wrapper around the GBIF Name parser to deal with the col Name class and API.
 */
public class NameParser implements Parser<ParsedNameUsage>, AutoCloseable {
  private static Logger LOG = LoggerFactory.getLogger(NameParser.class);
  public static final NameParser PARSER = new NameParser();
  private static final Pattern NORM_PUNCT_WS = Pattern.compile("\\s*([)}\\],;:]+)\\s*");
  private static final Pattern NORM_WS_PUNCT = Pattern.compile("\\s*([({\\[]+)\\s*");
  // UNICODE_CHARACTER_CLASS: Java's \b is ASCII-only, so without it the "et" of Behçet is a word ("Behç &")
  private static final Pattern NORM_AND = Pattern.compile("\\s*(\\b(?:and|et|und)\\b|(?:,\\s*)?&)\\s*", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern NORM_ET_AL = Pattern.compile("(&|\\bet) al\\b\\.?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern LEADING_PUNCT = Pattern.compile("^\\s*[.;,]\\s*");
  private static final Pattern SIC_CORRIG = Pattern.compile("\\s*[\\[(]?\\s*\\b(sic|corrig)\\b[.!\\s]*[\\])]?\\s*", Pattern.UNICODE_CHARACTER_CLASS);

  private static final String YEAR = "[12][0-9][0-9][0-9?]";
  private static final Pattern COMMA_BEFORE_YEAR = Pattern.compile("(?<!,)\\s+("+YEAR+")");
  private static final Pattern COMMA_AT_END = Pattern.compile("\\s*[,;:]\\s*$");
  // empty brackets, e.g. left behind by a removed taxonomic note: "Matsumoto, 1917 ()", or an unclosed one at the end
  private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\s*(?:\\(\\s*\\)|\\[\\s*]|\\{\\s*}|[(\\[{]\\s*$)");
  private static final Pattern NO_CHARS = Pattern.compile("^[^a-zA-Z0-9]+$");
  private static final Pattern NO_LETTERS_DIGITS = Pattern.compile("[^\\p{L}\\p{N}]+");
  // the first word of a note
  private static final Pattern NOTE_START = Pattern.compile("^[^\\p{L}]*(\\p{L}*).*$", Pattern.DOTALL);
  private static final Pattern NORM_WHITESPACE = Pattern.compile("(?:\\\\[nr]|\\s)+");

  private static final Map<String, Issue> WARN_TO_ISSUE = ImmutableMap.<String, Issue>builder()
      .put(Warnings.NULL_EPITHET, Issue.NULL_EPITHET)
      .put(Warnings.HOMOGLYHPS, Issue.HOMOGLYPH_CHARACTERS)
      .put(Warnings.UNUSUAL_CHARACTERS, Issue.UNUSUAL_NAME_CHARACTERS)
      .put(Warnings.SUBSPECIES_ASSIGNED, Issue.SUBSPECIES_ASSIGNED)
      .put(Warnings.LC_MONOMIAL, Issue.LC_MONOMIAL)
      .put(Warnings.INDETERMINED, Issue.INDETERMINED)
      .put(Warnings.HIGHER_RANK_BINOMIAL, Issue.HIGHER_RANK_BINOMIAL)
      .put(Warnings.QUESTION_MARKS_REMOVED, Issue.QUESTION_MARKS_REMOVED)
      .put(Warnings.REPL_ENCLOSING_QUOTE, Issue.REPL_ENCLOSING_QUOTE)
      .put(Warnings.MISSING_GENUS, Issue.MISSING_GENUS)
      .put(Warnings.DOUBTFUL_GENUS, Issue.DOUBTFUL_NAME)
      .put(Warnings.HTML_ENTITIES, Issue.ESCAPED_CHARACTERS)
      .put(Warnings.XML_TAGS, Issue.ESCAPED_CHARACTERS)
      .put(Warnings.BLACKLISTED_EPITHET, Issue.BLACKLISTED_EPITHET)
      .put(Warnings.NOMENCLATURAL_REFERENCE, Issue.CONTAINS_REFERENCE)
      .put(Warnings.AUTHORSHIP_REMOVED, Issue.AUTHORSHIP_REMOVED)
      .put(Warnings.UNLIKELY_YEAR, Issue.UNLIKELY_YEAR)
      .put(Warnings.UNCERTAIN_AUTHORSHIP, Issue.AUTHORSHIP_UNCERTAIN)
      .build();

  private Timer timer;
  private final NameParserRust parserInternal;

  NameParser() {
    this(new NameParserRust());
  }

  @VisibleForTesting
  NameParser(NameParserRust parser) {
    parserInternal = parser;
  }

  /**
   * @return the shared underlying name parser (the GBIF {@link org.gbif.nameparser.api.NameParser} API, backed by the
   *         rust implementation), for callers that need the raw {@link org.gbif.nameparser.api.ParsedName} without the
   *         overhead of building a full CoL {@link life.catalogue.api.model.Name}.
   */
  public org.gbif.nameparser.api.NameParser gbif() {
    return parserInternal;
  }

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
  @Override
  public Optional<ParsedNameUsage> parse(String name) {
    return parse(name, Rank.UNRANKED, null, IssueContainer.VOID);
  }

  public Optional<ParsedNameUsage> parse(SimpleName sn) {
    return parse(sn.getName(), sn.getAuthorship(), sn.getRank(), sn.getCode(), IssueContainer.VOID);
  }

  /**
   * @return a parsed authorship instance only, i.e. combination & original year & author list
   */
  public Optional<ParsedAuthorship> parseAuthorship(String authorship) {
    return parseAuthorship(authorship, null);
  }

  public Optional<ParsedAuthorship> parseAuthorship(String authorship, @Nullable NomCode code) {
    if (Strings.isNullOrEmpty(authorship)) return Optional.of(new ParsedAuthorship());
    // 5.0.0: parseAuthorship no longer throws — it returns Optional.empty() for an unparsable authorship.
    return parserInternal.parseAuthorship(authorship, code);
  }

  /**
   * Keeps the separately given authorship as the authorship string of a parsed name, cleaned but in its own spelling.
   * The parser has already parsed it into the name's authorship atoms, notes, original spelling and unparsed rest
   * together with the name, so what is left is to keep what it split off out of the string, or the label would show
   * it twice: a taxonomic note it moved out, a sic or corrig. it turned into the original spelling flag, and an
   * unparsed rest, which stays in the authorship string rather than becoming part of the scientific name.
   */
  private static void keepAuthorship(ParsedNameUsage pnu, String authorship) {
    Name n = pnu.getName();
    if (containsIgnoringWhitespace(authorship, n.getUnparsed())) {
      n.setUnparsed(null);
      n.rebuildScientificName();
    }
    setNormalizeAuthorship(pnu, authorship, noteInAuthorship(pnu.getTaxonomicNote(), authorship));
  }

  /**
   * The parsed taxonomic note comes from the name string and the authorship together, so it might be in the name
   * string only ("Abies alba sensu Smith" + "Mill."), in the authorship only, or in both, and with a spelling of its
   * own ("& al." for "et al."). The authorship can also be all note ("Abies alba sensu" + "auct NZ").
   *
   * @return the note to remove from the authorship string, or null if it is no part of it
   */
  @Nullable
  private static String noteInAuthorship(@Nullable String taxNote, String authorship) {
    if (taxNote == null) return null;
    if ((" " + words(taxNote) + " ").contains(" " + words(authorship) + " ")) {
      // nothing but (words of) the note: remove it all
      return authorship;
    }
    if (lettersAndDigits(authorship).contains(lettersAndDigits(taxNote))) {
      return taxNote;
    }
    // the note starts with a word of the authorship, e.g. emend. or sensu: try to remove it, but it is spelled
    // differently, so most likely the authorship gets rebuilt from its atoms
    String first = NOTE_START.matcher(taxNote).replaceFirst("$1").toLowerCase();
    return !first.isEmpty() && Pattern.compile("\\b" + Pattern.quote(first) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)
      .matcher(authorship).find() ? taxNote : null;
  }

  private static boolean containsIgnoringWhitespace(@Nullable String text, @Nullable String part) {
    return text != null && !StringUtils.isBlank(part) && StringUtils.deleteWhitespace(text).contains(StringUtils.deleteWhitespace(part));
  }

  private static String lettersAndDigits(String x) {
    return NO_LETTERS_DIGITS.matcher(x).replaceAll("").toLowerCase();
  }

  private static String words(String x) {
    return NO_LETTERS_DIGITS.matcher(x).replaceAll(" ").trim().toLowerCase();
  }

  /**
   * @return a regular expression matching the note with any whitespace, or none, between words and around punctuation,
   *         as the parser normalised its whitespace. Every character but letters and digits is matched literally.
   */
  static String note2pattern(String x) {
    StringBuilder sb = new StringBuilder();
    x.codePoints().forEach(cp -> {
      if (Character.isWhitespace(cp)) {
        sb.append(" *");
      } else if (Character.isLetterOrDigit(cp)) {
        sb.appendCodePoint(cp);
      } else {
        sb.append(" *").append(Pattern.quote(Character.toString(cp))).append(" *");
      }
    });
    return sb.toString();
  }

  static String setNormalizeAuthorship(ParsedNameUsage pnu, final String originalAuthorship, String taxNote) {
    String name = originalAuthorship;

    // we need to exclude the taxonomic bits from the authorship, otherwise we render them twice
    if (!StringUtils.isBlank(taxNote)) {
      // this is more tricky than it sounds as we altered the taxNote and it may have more/less whitespace in particular
      Pattern noteP = Pattern.compile("^(.*)" + note2pattern(taxNote) + "(.*)$", Pattern.CASE_INSENSITIVE);
      Matcher m = noteP.matcher(originalAuthorship);
      if (m.find()) {
        name = m.replaceFirst("$1 $2");
        // remove completely if no chars are left
        if (NO_CHARS.matcher(name).find()) {
          return null;
        }
        // remove final comma
        name = COMMA_AT_END.matcher(name).replaceFirst("");
      } else {
        // the pattern did not work, so the notes will be duplicated
        // better rebuild the authorship from scratch than keeping redundant data which gets duplicated in the label!
        LOG.debug("Failed to remove tax note >{}< from original authorship >{}<. Rebuild authorship from atoms instead", taxNote, originalAuthorship);
        pnu.getName().rebuildAuthorship();
        return pnu.getName().getAuthorship();
      }
    }

    // we need to remove the sic/corrig notes which live in originalSpelling flag now once parsed
    if (pnu.getName().isOriginalSpelling() != null) {
      name = SIC_CORRIG.matcher(name).replaceFirst("");
    }

    // remove empty brackets, also those a removed taxonomic note left behind
    name = EMPTY_BRACKETS.matcher(name).replaceAll("");

    // normalise different usages of ampersand, and, et &amp; to always use &
    name = NORM_AND.matcher(name).replaceAll(" & ");
    name = NORM_ET_AL.matcher(name).replaceAll("et al.");

    // put a comma before any year
    Matcher m = COMMA_BEFORE_YEAR.matcher(name);
    if (m.find()) {
      name = m.replaceFirst(", $1");
    }

    // remove leading punctuations and normalize subsequent ones
    name = LEADING_PUNCT.matcher(name).replaceAll("");
    name = NORM_WS_PUNCT.matcher(name).replaceAll(" $1");
    name = NORM_PUNCT_WS.matcher(name).replaceAll("$1 ");

    // finally whitespace and trimming
    name = NORM_WHITESPACE.matcher(name).replaceAll(" ");

    // apply to parsed name
    pnu.getName().setAuthorship(StringUtils.trimToNull(name));
    return pnu.getName().getAuthorship();
  }

  static <T> void setIfNull(T val, Supplier<T> getter, Consumer<T> setter) {
    if (val != null && getter.get() == null) {
      setter.accept(val);
    }
  }

  /**
   * Copies all authorship properties but the full authorship "cache"
   */
  private static void copyToPNU(ParsedAuthorship pn, ParsedNameUsage pnu, IssueContainer issues){
    pnu.getName().setCombinationAuthorship(pn.getCombinationAuthorship());
    pnu.getName().setBasionymAuthorship(pn.getBasionymAuthorship());
    // propagate notes and unparsed bits found in authorship if not already existing
    setIfNull(pn.getNomenclaturalNote(), pnu.getName()::getNomenclaturalNote, pnu.getName()::setNomenclaturalNote);
    // imprint year now lives on each Authorship (next to its year); surface it on the Name,
    // preferring the basionym (original publication) over the combination authorship
    Authorship basAuth = pn.getBasionymAuthorship();
    Authorship combAuth = pn.getCombinationAuthorship();
    String imprintYear = basAuth != null && basAuth.hasImprintYear() ? basAuth.getImprintYear()
                       : (combAuth != null ? combAuth.getImprintYear() : null);
    setIfNull(imprintYear, pnu.getName()::getImprintYear, pnu.getName()::setImprintYear);
    setIfNull(pn.getPublishedIn(), pnu::getPublishedIn, pnu::setPublishedIn);
    setIfNull(pn.getPublishedInYear(), pnu.getName()::getPublishedInYear, pnu.getName()::setPublishedInYear);
    setIfNull(pn.getTaxonomicNote(), pnu::getTaxonomicNote, pnu::setTaxonomicNote);
    // authorship-only parses return a plain ParsedAuthorship; originalSpelling only exists on a full ParsedName
    if (pn instanceof ParsedName pnn && pnn.isOriginalSpelling() != null) {
      pnu.getName().setOriginalSpelling(pnn.isOriginalSpelling());
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
        issues.add(Issue.PARTIALLY_PARSABLE_NAME);
        break;
      case NONE:
        issues.add(Issue.UNPARSABLE_NAME);
        break;
    }

    if (pn.isDoubtful()) {
      pnu.setDoubtful(true);
      issues.add(Issue.DOUBTFUL_NAME);
    }
    // translate warnings into issues
    for (String warn : pn.getWarnings()) {
      if (WARN_TO_ISSUE.containsKey(warn)) {
        issues.add(WARN_TO_ISSUE.get(warn));
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
    return parse(name, null, rank, code, issues);
  }

  /**
   * Fully parses a name using #parse(String, Rank) but converts names that throw a UnparsableException
   * into ParsedName objects with the scientific name, rank and name type given.
   */
  public Optional<ParsedNameUsage> parse(String name, String authorship, Rank rank, NomCode code, IssueContainer issues) {
    Name n = new Name();
    n.setScientificName(name);
    n.setAuthorship(authorship);
    n.setRank(rank);
    n.setCode(code);
    return parse(n, issues);
  }

  /**
   * Parses the scientific name of a given name instance together with its authorship, if it has one,
   * and populates the instance with the result. Names the parser cannot parse keep their scientific name and
   * authorship as they are, with the name type and code the parser detected.
   *
   * @return the parsed name usage, or empty for a blank scientific name
   */
  public Optional<ParsedNameUsage> parse(Name n, IssueContainer issues) {
    if (StringUtils.isBlank(n.getScientificName())) {
      return Optional.empty();
    }
    ParsedNameUsage pnu;
    Timer.Context ctx = timer == null ? null : timer.time();
    try {
      final String authorship = StringUtils.trimToNull(n.getAuthorship());
      // one parse of name and authorship together: the parser decides between the separate authorship and one in the
      // name string, infers the code from both and moves notes and sic/corrig out of the authorship.
      // 5.0.0: parse() never throws — it returns a sealed three-way ParseResult.
      switch (parserInternal.parse(n.getScientificName(), authorship, n.getRank(), n.getCode())) {
        case ParseResult.Parsed p -> {
          pnu = fromParsedName(n, p.name(), issues);
          if (authorship != null) {
            keepAuthorship(pnu, authorship);
          }
        }
        case ParseResult.Informal inf -> {
          // 5.0.0 semistructured band: a supraspecific anchor carrying a provisional designation
          // (e.g. "Rhizobium sp. RMCC TR1811", "Bartonella group"). Rebuild the type=INFORMAL
          // ParsedName the 4.x parser used to return so all downstream handling stays unchanged.
          pnu = fromParsedName(n, inf.toParsedName(), issues);
          // 5.0's Informal result is lean (no warnings). A species-level informal ("Genus sp./
          // species N") is indeterminate, so re-flag it: NameInterpreter keys its rank inference on
          // INFORMAL + INDETERMINED and would otherwise drop the parser's rank (e.g. SPECIES) to
          // UNRANKED, exactly as the 4.x INFORMAL + INDETERMINED signal drove it.
          if (inf.rank() != null && inf.rank().isSpeciesOrBelow()) {
            issues.add(Issue.INDETERMINED);
          }
          // the parser keeps the authorship of an informal name in its phrase ("Geranium" + "L." -> "Geranium sp. L."),
          // which is the name's unparsed portion now. Adding it as authorship as well would double it in the label
          if (authorship != null && !containsIgnoringWhitespace(pnu.getName().getUnparsed(), authorship)) {
            setNormalizeAuthorship(pnu, authorship, null);
          }
        }
        case ParseResult.Unparsable e -> {
          pnu = new ParsedNameUsage();
          pnu.setName(n);
          pnu.getName().setScientificName(e.name());
          pnu.getName().setType(e.type());
          // name-parser 5.0 carries a NomCode on the unparsable for code-known names (e.g. NomCode.VIRUS).
          // Record it so true virus names keep their virus signal now that NameType.VIRUS is gone.
          if (e.code() != null) {
            pnu.getName().setCode(e.code());
          }
          // adds an issue in case the type indicates a parsable name
          if (pnu.getName().getType().isParsable()) {
            issues.add(Issue.UNPARSABLE_NAME);
          }
        }
      }
    } finally {
      if (ctx != null) {
        ctx.stop();
      }
    }
    return Optional.of(pnu);
  }

  /**
   * Uses an existing name instance to populate from a ParsedName instance
   */
  private static ParsedNameUsage fromParsedName(Name n, ParsedName pn, IssueContainer issues) {
    // the Name class has no phrase, it becomes the start of the unparsed portion
    if (!StringUtils.isBlank(pn.getPhrase())) {
      pn.setState(ParsedName.State.PARTIAL);
    }
    ParsedNameUsage pnu = new ParsedNameUsage();
    pnu.setName(n);
    copyToPNU(pn, pnu, issues);
    n.setUnparsed(StringUtils.trimToNull(StringUtils.joinWith(" ", StringUtils.trimToEmpty(pn.getPhrase()), StringUtils.trimToEmpty(pn.getUnparsed()))));
    // name specifics
    n.setUninomial(pn.getUninomial());
    n.setGenus(pn.getGenus());
    n.setInfragenericEpithet(pn.getInfragenericEpithet());
    n.setSpecificEpithet(pn.getSpecificEpithet());
    n.setInfraspecificEpithet(pn.getInfraspecificEpithet());
    n.setCultivarEpithet(pn.getCultivarEpithet());
    // keep a concrete caller-supplied rank over the parser's generic guess, but only when it agrees
    // with the parsed name shape; a source may mislabel a trinomial as [species] - don't retain that
    // contradiction, fall back to the parser's (infraspecific) rank instead
    if (pn.getRank() != null &&
        (n.getRank() == null || n.getRank().isUncomparable() || pn.getRank().isInfraspecific() != n.getRank().isInfraspecific())) {
      n.setRank(pn.getRank());
    }
    if (n.getCode() == null) {
      n.setCode(pn.getCode());
    }
    n.setCandidatus(pn.isCandidatus());
    if (pn.getNotho() != null) {
      pn.getNotho().forEach(n::addNotho);
    }
    n.setType(pn.getType());

    if (pn.isIncomplete()) {
      issues.add(Issue.INCONSISTENT_NAME);
    }

    // the parser can capture authorship on the genus or species part of a more specific name
    // (e.g. the genus author in "Cordia (Adans.) Kuntze sect. Salimori"). The Name model only keeps
    // the terminal authorship, so flag these superfluous authorships as they are not retained.
    if (pn.hasGenericAuthorship() || pn.hasSpecificAuthorship()) {
      issues.add(Issue.SUPERFLUOUS_AUTHORSHIP);
    }

    // we rebuilt the caches as we dont have any original authorship yet - it all came in through the single scientificName
    n.rebuildScientificName();
    n.rebuildAuthorship();
    return pnu;
  }

  @Override
  public void close() throws Exception {
    // nothing to close, the rust parser holds no resources
  }

}
