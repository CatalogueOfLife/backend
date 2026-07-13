package life.catalogue.parser;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.NameFormatter;

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
  private static final Pattern NORM_AND = Pattern.compile("\\s*(\\b(?:and|et|und)\\b|(?:,\\s*)?&)\\s*");
  private static final Pattern NORM_ET_AL = Pattern.compile("(&|\\bet) al\\b\\.?");
  private static final Pattern NORM_ANON = Pattern.compile("\\b(anon\\.?)(\\b|\\s|$)");
  private static final Pattern LEADING_PUNCT = Pattern.compile("^\\s*[.;,]\\s*");
  private static final Pattern SIC_CORRIG = Pattern.compile("\\s*[\\[(]?\\s*\\b(sic|corrig)\\b[.!\\s]*[\\])]?\\s*");

  private static final String YEAR = "[12][0-9][0-9][0-9?]";
  private static final Pattern COMMA_BEFORE_YEAR = Pattern.compile("(?<!,)\\s+("+YEAR+")");
  private static final Pattern COMMA_AT_END = Pattern.compile("\\s*[,;:]\\s*$");
  private static final Pattern NO_CHARS = Pattern.compile("^[^a-zA-Z0-9]+$");
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
   * Populates the parsed authorship of a given name instance by parsing a single authorship string.
   * Only parses the authorship if the name itself is already parsed.
   */
  public void parseAuthorshipIntoName(ParsedNameUsage pnu, final String authorship, IssueContainer v) {
    // try to add an authorship if not yet there
    if (!Strings.isNullOrEmpty(authorship)) {
      if (pnu.getName().isParsed()) {
        ParsedAuthorship pnAuthorship = parseAuthorship(authorship, pnu.getName().getCode()).orElseGet(() -> {
          LOG.info("Unparsable authorship {}", authorship);
          v.add(Issue.UNPARSABLE_AUTHORSHIP);
          // add the full, unparsed authorship in this case to not lose it
          ParsedName pn = new ParsedName();
          pn.getCombinationAuthorship().getAuthors().add(authorship);
          return pn;
        });

        // we might have already parsed an authorship from the scientificName string which does not match up?
        if (pnu.getName().hasParsedAuthorship()) {
          String prevAuthorship = NameFormatter.authorship(pnu.getName(), false);
          if (!prevAuthorship.equalsIgnoreCase(pnAuthorship.authorshipComplete(pnu.getName().getCode()))) {
            v.add(Issue.INCONSISTENT_AUTHORSHIP);
            LOG.info("Different authorship found in name {} than in parsed version: [{}] vs [{}]",
                pnu.getName(), prevAuthorship, pnAuthorship.authorshipComplete(pnu.getName().getCode()));
          }
        }

        // keep authorship issues in a separate container
        // so we can filter out non authorship related issues before we add them to the verbatim record
        IssueContainer ic = new IssueContainer.Simple();
        copyToPNU(pnAuthorship, pnu, ic);
        // ignore issues related to the epithet - we only parse authorships here
        removeEpithetIssues(ic);
        ic.getIssues().forEach(v::add);

        // a standalone authorship is parsed via parseAuthorship which returns a plain ParsedAuthorship
        // and cannot carry originalSpelling, so recover a sic/corrig marker from the raw authorship here
        // (sic = original spelling kept, corrig. = corrected spelling). setNormalizeAuthorship then strips it.
        if (pnu.getName().isOriginalSpelling() == null) {
          Matcher sc = SIC_CORRIG.matcher(authorship);
          if (sc.find()) {
            pnu.getName().setOriginalSpelling("sic".equalsIgnoreCase(sc.group(1)));
          }
        }

        // use original authorship string but normalize whitespace and remove taxonomic notes, e.g. misapplication
        setNormalizeAuthorship(pnu, authorship, pnAuthorship.getTaxonomicNote());

      } else {
        // unparsed name might still not have any authorship
        String sciName = AuthorshipNormalizer.normalize(pnu.getName().getScientificName());
        if (sciName == null) {
          pnu.getName().setAuthorship(authorship);
        } else {
          String authNormed = AuthorshipNormalizer.normalize(authorship);
          if (authNormed != null && !sciName.contains(authNormed)) {
            pnu.getName().setAuthorship(authorship);
          }
        }
      }
    }
  }

  private static void removeEpithetIssues(IssueContainer v) {
    v.remove(Issue.NULL_EPITHET);
    v.remove(Issue.SUBSPECIES_ASSIGNED);
    v.remove(Issue.LC_MONOMIAL);
    v.remove(Issue.QUESTION_MARKS_REMOVED);
    v.remove(Issue.REPL_ENCLOSING_QUOTE);
    v.remove(Issue.ESCAPED_CHARACTERS);
    v.remove(Issue.ESCAPED_CHARACTERS);
    v.remove(Issue.CONTAINS_REFERENCE);
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

    // normalise different usages of ampersand, and, et &amp; to always use &
    name = NORM_AND.matcher(name).replaceAll(" & ");
    name = NORM_ET_AL.matcher(name).replaceAll("et al.");

    // put a comma before any year
    Matcher m = COMMA_BEFORE_YEAR.matcher(name);
    if (m.find()) {
      name = m.replaceFirst(", $1");
    }

    // capitalize Anonymous author
    m = NORM_ANON.matcher(name);
    if (m.find()) {
      name = m.replaceFirst("Anon.");
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
      // parse name and authorship together so the parser can infer the code/originalSpelling from both.
      // 5.0.0: parse() never throws — it returns a sealed three-way ParseResult.
      switch (parserInternal.parse(n.getScientificName(), authorship, n.getRank(), n.getCode())) {
        case ParseResult.Parsed p -> {
          pnu = fromParsedName(n, p.name(), issues);
          // CoL post-processing: normalized authorship string + UNPARSABLE/INCONSISTENT flags
          parseAuthorshipIntoName(pnu, authorship, issues);
        }
        case ParseResult.Informal inf -> {
          // 5.0.0 semistructured band: a supraspecific anchor carrying a provisional designation
          // (e.g. "Rhizobium sp. RMCC TR1811", "Bartonella group"). Rebuild the type=INFORMAL
          // ParsedName the 4.x parser used to return so all downstream handling stays unchanged.
          pnu = fromParsedName(n, inf.toParsedName(), issues);
          parseAuthorshipIntoName(pnu, authorship, issues);
        }
        case ParseResult.Unparsable e -> {
          pnu = new ParsedNameUsage();
          pnu.setName(n);
          pnu.getName().setRank(n.getRank());
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
  
  public Optional<NameType> determineType(Name name) {
    String sciname = name.getScientificName();
    if (StringUtils.isBlank(sciname)) {
      return Optional.of(NameType.OTHER);
    }
    // 5.0.0: type() is available on every ParseResult variant (Parsed/Informal/Unparsable), no throw.
    ParseResult result = parserInternal.parse(sciname, null, name.getRank(), name.getCode());
    return Optional.of(ObjectUtils.coalesce(result.type(), NameType.SCIENTIFIC));
  }

  /**
   * Uses an existing name instance to populate from a ParsedName instance
   */
  private static ParsedNameUsage fromParsedName(Name n, ParsedName pn, IssueContainer issues) {
    // if we parsed phrases we do not keep them in the Name class
    if (!StringUtils.isBlank(pn.getPhrase())) {
      if (pn.getUnparsed() != null) {
        LOG.warn("Partially parsed name >{}< contains a phrase >{}< and an unparsed portion >{}<", n.getScientificName(), pn.getPhrase(), pn.getUnparsed());
      }
      pn.setState(ParsedName.State.PARTIAL);
      StringBuilder sb = new StringBuilder();
      sb.append(pn.getPhrase());
      n.setUnparsed(sb.toString());
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
    n.setOriginalSpelling(pn.isOriginalSpelling());
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
    // nothing to close in the v4 parser
  }

}
