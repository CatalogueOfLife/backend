package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.validation.constraints.Null;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NameType;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import static life.catalogue.matching.NameValidator.MAX_YEAR;
import static life.catalogue.matching.NameValidator.MIN_YEAR;
import static life.catalogue.parser.SafeParser.parse;

/**
 * Base interpreter providing common methods for both ACEF and DWC
 */
public class InterpreterBase {
  
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterBase.class);
  protected static final Pattern AREA_VALUE_PATTERN = Pattern.compile("[\\w\\s:.-]+", Pattern.UNICODE_CHARACTER_CLASS);
  static final Pattern SEC_REF = Pattern.compile("\\b(sensu|sec\\.?|fide|auct\\.?|according to) (?!lat|str|non|nec|auct(?:orum)?)(.{3,})$", Pattern.CASE_INSENSITIVE);
  private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{3})(\\d|\\s*\\?)(?:-[0-9-]+)?$");
  private static final Pattern SPLIT_COMMA = Pattern.compile("(?<!\\\\),");
  protected final NeoDb store;
  protected final DatasetSettings settings;
  protected final NameInterpreter nameInterpreter;
  private final Gazetteer distributionStandard;
  protected final ReferenceFactory refFactory;

  /**
   * @param settings
   * @param refFactory
   * @param store
   * @param preferAtomsDefault should name atoms be preferred over the scientificName by default, i.e. if no dataset setting exists?
   */
  public InterpreterBase(DatasetSettings settings, ReferenceFactory refFactory, NeoDb store, boolean preferAtomsDefault) {
    this.settings = settings;
    this.refFactory = refFactory;
    this.store = store;
    nameInterpreter = new NameInterpreter(settings, preferAtomsDefault);
    if (settings.has(Setting.DISTRIBUTION_GAZETTEER)) {
      distributionStandard = settings.getEnum(Setting.DISTRIBUTION_GAZETTEER);
      LOG.info("Dataset wide distribution standard {} found in settings", distributionStandard);
    } else {
      LOG.info("No dataset wide distribution standard found in settings");
      distributionStandard = null;
    }
  }
  
  protected boolean requireTerm(VerbatimRecord v, Term term, Issue notExistingIssue){
    if (!v.hasTerm(term)) {
      v.addIssue(notExistingIssue);
      return false;
    }
    return true;
  }

  /**
   * Strips all html tags if they exist and optionally converts link to markdown links.
   */
  protected static String replaceHtml(String x, boolean useMarkdownLinks) {
    if (StringUtils.isBlank(x)) return null;

    var doc = Jsoup.parse(x);
    if (useMarkdownLinks) {
      var links = doc.select("a");
      for (var link : links) {
        String url = link.attr("href");
        if (!StringUtils.isBlank(url)) {
          String md = String.format("[%s](%s)", link.text(), url);
          link.text(md);
        }
      }
    }
    return doc.wholeText().trim();
  }

  protected static String getFormattedText(VerbatimRecord v, Term term) {
    if (term != null) {
      return replaceHtml(v.get(term), true);
    }
    return null;
  }

  protected Reference setReference(VerbatimRecord v, Term refIdTerm, Consumer<String> refIdConsumer){
    return setReference(v, refIdTerm, refIdConsumer, null);
  }

  protected Reference setReference(VerbatimRecord v, Term refIdTerm, Consumer<String> refIdConsumer, @Nullable Consumer<String> refCitationConsumer){
    Reference ref = null;
    if (v.hasTerm(refIdTerm)) {
      String rid = v.getRaw(refIdTerm);
      ref = refFactory.find(rid, null);
      if (ref == null) {
        LOG.debug("ReferenceID {} not existing but referred from {} in file {} line {}", rid, refIdTerm.prefixedName(), v.getFile(), v.fileLine());
        v.addIssue(Issue.REFERENCE_ID_INVALID);
      } else {
        refIdConsumer.accept(ref.getId());
        if (refCitationConsumer != null) {
          refCitationConsumer.accept(ref.getCitation());
        }
      }
    }
    return ref;
  }

  protected void setReferences(VerbatimRecord v, Term refIdTerm, Splitter splitter, Consumer<List<String>> refIdConsumer){
    if (v.hasTerm(refIdTerm)) {
      String rids = v.getRaw(refIdTerm);
      if (rids != null) {
        List<String> existingIds = new ArrayList<>();
        for (String rid : splitter.split(rids)) {
          Reference ref = refFactory.find(rid);
          if (ref == null) {
            LOG.debug("ReferenceID {} not existing but referred from {} in file {} line {}", rid, refIdTerm.prefixedName(), v.getFile(), v.fileLine());
            v.addIssue(Issue.REFERENCE_ID_INVALID);
          } else {
            existingIds.add(ref.getId());
          }
        }
        refIdConsumer.accept(existingIds);
      }
    }
  }

  /**
   * Sets a taxonomic note to usage namePhrase or a proper accordingTo reference if parsable
   */
  protected void setTaxonomicNote(NameUsage u, String taxNote, VerbatimRecord v) {
    if (!StringUtils.isBlank(taxNote)) {
      v.addIssue(Issue.AUTHORSHIP_CONTAINS_TAXONOMIC_NOTE);
      Matcher m = SEC_REF.matcher(taxNote);
      if (m.find()) {
        setAccordingTo(u, m.group(2).trim(), v);
        String remainder = m.replaceFirst("");
        if (!StringUtils.isBlank(remainder)) {
          u.setNamePhrase(remainder.trim());
        }
      } else {
        u.setNamePhrase(taxNote);
      }
    }
  }

  public DatasetSettings getSettings() {
    return settings;
  }

  protected void setAccordingTo(NameUsage u, String accordingTo, VerbatimRecord v) {
    if (accordingTo != null) {
      Reference ref = buildReference(accordingTo, v);
      if (ref != null) {
        if (u.getAccordingToId() != null) {
          v.addIssue(Issue.ACCORDING_TO_CONFLICT);
        }
        u.setAccordingToId(ref.getId());
        u.setAccordingTo(accordingTo);
      }
    }
  }

  protected void setPublishedIn(Name n, String publishedIn, VerbatimRecord v) {
    Reference ref = buildReference(publishedIn, v);
    if (ref != null) {
      n.setPublishedInId(ref.getId());
      n.setPublishedInPage(ref.getPage());
      n.setPublishedInYear(ref.getYear());
    }
  }

  protected Reference buildReference(String citation, VerbatimRecord v) {
    Reference ref = null;
    if (!StringUtils.isBlank(citation)){
      ref = refFactory.fromCitation(null, citation, v);
      if (ref.getVerbatimKey() == null) {
        // create new reference with verbatim key, we've never seen this before!
        ref.setVerbatimKey(v.getId());
        store.references().create(ref);
      }
    }
    return ref;
  }

  protected List<TaxonProperty> interpretProperty(VerbatimRecord rec, BiConsumer<TaxonProperty, VerbatimRecord> addReference,
                                                  Term property, Term value, @Nullable Term ordinal, @Nullable Term page, @Nullable Term remarks) {
    if (rec.hasTerm(property) && rec.hasTerm(value)) {
      var tp = new TaxonProperty();
      tp.setVerbatimKey(rec.getId());
      tp.setProperty(rec.get(property));
      tp.setValue(getFormattedText(rec, value));
      if (page != null) {
        tp.setPage(rec.get(page));
      }
      if (ordinal != null) {
        tp.setOrdinal(rec.getInt(ordinal, Issue.ORDINAL_INVALID));
      }
      if (remarks != null) {
        tp.setRemarks(getFormattedText(rec, remarks));
      }
      addReference.accept(tp, rec);

      return Lists.newArrayList(tp);
    }
    return Collections.emptyList();
  }

  protected List<VernacularName> interpretVernacular(VerbatimRecord rec, BiConsumer<VernacularName, VerbatimRecord> addReference,
                                                     Term name, @Nullable Term translit, @Nullable Term preferred, @Nullable Term lang, @Nullable Term sex,
                                                     @Nullable Term remarks, @Nullable Term area, @Nullable Term... countryTerms) {
    String vname = rec.get(name);
    if (vname != null) {
      VernacularName vn = new VernacularName();
      vn.setVerbatimKey(rec.getId());
      vn.setName(vname);
      vn.setRemarks(getFormattedText(rec, remarks));
      
      if (translit != null) {
        vn.setLatin(rec.get(translit));
      }
      if (preferred != null) {
        vn.setPreferred(bool(rec, Issue.VERNACULAR_PREFERRED, preferred));
      }
      if (lang != null) {
        vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(lang)).orNull(Issue.VERNACULAR_LANGUAGE_INVALID, rec));
      }
      if (sex != null) {
        vn.setSex(SafeParser.parse(SexParser.PARSER, rec.get(sex)).orNull(Issue.VERNACULAR_SEX_INVALID, rec));
      }
      if (area != null) {
        vn.setArea(rec.get(area));
      }
      if (countryTerms != null) {
        vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(countryTerms)).orNull(Issue.VERNACULAR_COUNTRY_INVALID, rec));
      }

      addReference.accept(vn, rec);
  
      return Lists.newArrayList(vn);
    }
    return Collections.emptyList();
  }
  
  protected List<Distribution> interpretDistributionByGazetteer(VerbatimRecord rec, BiConsumer<Distribution, VerbatimRecord> addReference,
                                                                Term tArea, Term tGazetteer, Term tStatus, Term tRemarks) {
    // require location
    if (rec.hasTerm(tArea)) {
      // which standard?
      Gazetteer gazetteer;
      if (!rec.hasTerm(tGazetteer) && distributionStandard != null) {
        gazetteer = distributionStandard;
      } else {
        gazetteer = parse(GazetteerParser.PARSER, rec.get(tGazetteer))
            .orElse(Gazetteer.TEXT, Issue.DISTRIBUTION_GAZETEER_INVALID, rec);
      }
      return createDistributions(gazetteer, rec.get(tArea), rec.get(tStatus), rec, tRemarks, addReference);
    }
    return Collections.emptyList();
  }
  
  protected static List<Distribution> createDistributions(@Nullable Gazetteer standard, final String locRaw, String statusRaw, VerbatimRecord rec,
                                                   Term tRemarks,
                                                   BiConsumer<Distribution, VerbatimRecord> addReference) {
    if (locRaw != null) {

      final DistributionStatus status = parse(DistributionStatusParser.PARSER, statusRaw)
          .orElse(DistributionStatus.NATIVE, Issue.DISTRIBUTION_STATUS_INVALID, rec);

      if (standard == Gazetteer.TEXT) {
        return Lists.newArrayList( createDistribution(rec, new AreaImpl(locRaw), status, tRemarks, addReference) );
      
      } else {
        List<Distribution> distributions = new ArrayList<>();
        List<String> values;

        var ns = AreaParser.parsePrefix(locRaw);
        if (ns != null && (ns.equals("http") || ns.equals("https") || ns.equals("ftp") || ns.equals("urn"))) {
          values = List.of(locRaw);
        } else {
          values = words(locRaw);
        }

        for (String loc : values) {
          // add gazetteer prefix for the parser if not yet included
          if (standard != null && loc.indexOf(':') < 0) {
            loc = standard.locationID(loc);
          }
          var area = SafeParser.parse(AreaParser.PARSER, loc).orNull(Issue.DISTRIBUTION_AREA_INVALID, rec);
          if (area != null) {
            // check if we have contradicting extracted a gazetteer
            if (standard != null && area.getGazetteer() != Gazetteer.TEXT && area.getGazetteer() != standard) {
              LOG.info("Area standard {} found in area {} different from explicitly given standard {} for {}",
                area.getGazetteer(), area.getGazetteer(), standard, rec);
            }
            distributions.add(createDistribution(rec, area, status, tRemarks, addReference));
          }
        }
        return distributions;
      }
    }
    return Collections.emptyList();
  }

  private static List<String> words(String x) {
    if (x == null) return Collections.EMPTY_LIST;
    Matcher m = AREA_VALUE_PATTERN.matcher(x);
    List<String> words = new ArrayList<>();
    while (m.find()) {
      words.add(m.group(0).trim());
    }
    return words;
  }

  private static Distribution createDistribution(VerbatimRecord rec, Area area, DistributionStatus status, Term tRemarks, BiConsumer<Distribution, VerbatimRecord> addReference) {
    Distribution d = new Distribution();
    d.setVerbatimKey(rec.getId());
    d.setArea(area);
    d.setStatus(status);
    d.setRemarks(getFormattedText(rec, tRemarks));
    addReference.accept(d, rec);
    return d;
  }

  protected List<Media> interpretMedia(VerbatimRecord rec, BiConsumer<Media, VerbatimRecord> addReference,
                                       Set<Term> type, Set<Term> url, Set<Term> link, Set<Term> license, Set<Term> creator, Set<Term> created, Set<Term> title, Set<Term> format, Set<Term> remarks) {
    return interpretMedia(rec, addReference, selectFirst(type, rec), selectFirst(url, rec), selectFirst(link, rec), selectFirst(license, rec), selectFirst(creator, rec), selectFirst(created, rec), selectFirst(title, rec), selectFirst(format, rec), selectFirst(remarks, rec));
  }

  private static Term selectFirst(Set<Term> terms, VerbatimRecord rec) {
    Term first = null;
    for (Term t : terms) {
      if (first == null) {
        first = t;
      }
      if (rec.hasTerm(t)) return t;
    }
    return first;
  }

  protected List<Media> interpretMedia(VerbatimRecord rec, BiConsumer<Media, VerbatimRecord> addReference,
                 Term type, Term url, Term link, Term license, Term creator, Term created, Term title, Term format, Term remarks) {
    // require media or link url
    if (rec.hasTerm(url) || rec.hasTerm(link)) {
      Media m = new Media();
      m.setVerbatimKey(rec.getId());
      m.setUrl( uri(rec, Issue.URL_INVALID, url));
      m.setLink( uri(rec, Issue.URL_INVALID, link));
      m.setLicense( SafeParser.parse(LicenseParser.PARSER, rec.get(license)).orNull() );
      m.setCapturedBy(rec.get(creator));
      m.setCaptured( date(rec, Issue.MEDIA_CREATED_DATE_INVALID, created) );
      m.setTitle(rec.get(title));
      m.setFormat(MediaInterpreter.parseMimeType(rec.get(format)));
      m.setType( SafeParser.parse(MediaTypeParser.PARSER, rec.get(type)).orNull() );
      m.setRemarks(getFormattedText(rec, remarks));
      MediaInterpreter.detectType(m);
      
      addReference.accept(m, rec);
  
      return Lists.newArrayList(m);
    }
    return Collections.emptyList();
  }

  protected LocalDate date(VerbatimRecord v, Issue invalidIssue, Term term) {
    FuzzyDate fd = fuzzydate(v, invalidIssue, term);
    return fd == null ? null : fd.toLocalDate();
  }

  protected FuzzyDate fuzzydate(VerbatimRecord v, Issue invalidIssue, Term term) {
    Optional<FuzzyDate> date;
    try {
      date = DateParser.PARSER.parse(v.get(term));
    } catch (UnparsableException e) {
      v.addIssue(invalidIssue);
      return null;
    }
    if (date.isPresent()) {
      if (date.get().isFuzzyDate()) {
        v.addIssue(Issue.PARTIAL_DATE);
      }
      return date.get();
    }
    return null;
  }

  protected URI uri(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return parse(UriParser.PARSER, v.getFirstRaw(terms)).orNull(invalidIssue, v);
  }

  protected Integer integer(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return SafeParser.parse(IntegerParser.PARSER, v.getFirstRaw(terms)).orNull(invalidIssue, v);
  }

  protected Double decimal(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return SafeParser.parse(DecimalParser.PARSER, v.getFirstRaw(terms)).orNull(invalidIssue, v);
  }

  protected Boolean bool(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return parse(BooleanParser.PARSER, v.getFirst(terms)).orNull(invalidIssue, v);
  }
  
  protected Boolean bool(VerbatimRecord v, Term... terms) {
    return parse(BooleanParser.PARSER, v.getFirst(terms)).orNull();
  }

  protected boolean bool(VerbatimRecord v, Issue invalidIssue, boolean defaultValue, Term... terms) {
    return parse(BooleanParser.PARSER, v.getFirst(terms)).orElse(defaultValue);
  }

  protected static List<Identifier> interpretIdentifiers(String idsRaw, @Nullable Identifier.Scope defaultScope, IssueContainer issues) {
    if (!StringUtils.isBlank(idsRaw)) {
      List<Identifier> ids = new ArrayList<>();
      for (String altID : SPLIT_COMMA.split(idsRaw)) {
        var id = Identifier.parse(altID);
        ids.add(id);
        if (id.isLocal()) {
          if (defaultScope != null) {
            id.setScope(defaultScope);
          } else {
            issues.addIssue(Issue.IDENTIFIER_WITHOUT_SCOPE);
          }
        }
      }
      return ids;
    }
    return Collections.emptyList();
  }

  public NeoUsage interpretUsage(Term idTerm, ParsedNameUsage pnu, Term taxStatusTerm, TaxonomicStatus defaultStatus, VerbatimRecord v, Map<Term, Identifier.Scope> altIdTerms) {
    NeoUsage u;
    // a synonym by status?
    EnumNote<TaxonomicStatus> status = SafeParser.parse(TaxonomicStatusParser.PARSER, v.get(taxStatusTerm))
      .orElse(()->new EnumNote<>(defaultStatus, null), Issue.TAXONOMIC_STATUS_INVALID, v);

    if (status.val.isBareName()) {
      u = NeoUsage.createBareName(Origin.SOURCE, pnu.getName());
    } else if (status.val.isSynonym()) {
      u = NeoUsage.createSynonym(Origin.SOURCE, pnu.getName(), status.val);
      if (pnu.isExtinct()) {
        // flag this as synonyms cannot have the extinct flag
        v.addIssue(Issue.NAME_CONTAINS_EXTINCT_SYMBOL);
      }
    } else {
      u = NeoUsage.createTaxon(Origin.SOURCE, pnu.getName(), status.val);
      var t = (Taxon) u.usage;
      if (pnu.isExtinct()) {
        t.setExtinct(true);
      } else if (settings.containsKey(Setting.EXTINCT)) {
        t.setExtinct(settings.getBool(Setting.EXTINCT));
      }
      if (pnu.isDoubtful()) {
        t.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
      }
    }

    // shared usage props
    u.setId(v.getRaw(idTerm));
    u.setVerbatimKey(v.getId());
    setTaxonomicNote(u.usage, pnu.getTaxonomicNote(), v);
    u.homotypic = TaxonomicStatusParser.isHomotypic(status);

    if (u.isNameUsageBase()) {
      List<Identifier> ids = new ArrayList<>();
      for (var te : altIdTerms.entrySet()) {
        var x = interpretIdentifiers(v.getRaw(te.getKey()), te.getValue(), v);
        if (x != null) {
          ids.addAll(x);
        }
      }
      ((NameUsageBase) u.usage).setIdentifier(ids);
    }
    // flat classification via dwc or coldp
    u.classification = new Classification();
    if (v.hasDwcTerms()) {
      for (DwcTerm t : DwcTerm.HIGHER_RANKS) {
        u.classification.setByTerm(t, v.get(t));
      }
    }

    if (v.hasColdpTerms()) {
      for (ColdpTerm t : ColdpTerm.DENORMALIZED_RANKS) {
        u.classification.setByTerm(t, v.get(t));
      }
    }
    return u;
  }

  protected void setEnvironment(Taxon t, VerbatimRecord v, Term environment) {
    String raw = v.get(environment);
    if (raw != null) {
      for (String lzv : words(raw)) {
        Environment lz = parse(EnvironmentParser.PARSER, lzv).orNull(Issue.ENVIRONMENT_INVALID, v);
        if (lz != null) {
          t.getEnvironments().add(lz);
        }
      }
    }
    if (t.getEnvironments() == null || t.getEnvironments().isEmpty() && settings.containsKey(Setting.ENVIRONMENT)) {
      t.setEnvironments(Set.of(settings.getEnum(Setting.ENVIRONMENT)));
    }
  }
  
  protected static Integer parseNomenYear(Term term, VerbatimRecord v) {
    return parseNomenYear(v.get(term), v);
  }

  /**
   * Parses the nomenclatural year the name was published and flags issues if the year is unparsable or unliklely.
   */
  protected static Integer parseNomenYear(String year, IssueContainer issues) {
    if (!StringUtils.isBlank(year)) {
      Matcher m = YEAR_PATTERN.matcher(year.trim());
      if (m.find()) {
        Integer y;
        if (m.group(2).equals("?")) {
          // convert ? to a zero
          y = Integer.parseInt(m.group(1)+"0");
        } else {
          y = Integer.parseInt(m.group(1)+m.group(2));
        }
        if (y < MIN_YEAR || y > MAX_YEAR) {
          issues.addIssue(Issue.UNLIKELY_YEAR);
        } else {
          return y;
        }

      } else {
        issues.addIssue(Issue.UNPARSABLE_YEAR);
      }
    }
    return null;
  }

}
