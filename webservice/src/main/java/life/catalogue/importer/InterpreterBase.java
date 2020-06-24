package life.catalogue.importer;

import com.google.common.collect.Lists;
import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.*;
import org.apache.commons.lang3.StringUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static life.catalogue.parser.SafeParser.parse;

/**
 * Base interpreter providing common methods for both ACEF and DWC
 */
public class InterpreterBase {
  
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterBase.class);
  protected static final Pattern AREA_VALUE_PATTERN = Pattern.compile("[\\w\\s:.-]+", Pattern.UNICODE_CHARACTER_CLASS);
  static final Pattern SEC_REF = Pattern.compile("\\b(sensu|sec\\.?|fide|auct\\.?|according to) (?!lat|str|non|nec|auct(?:orum)?)(.{3,})$", Pattern.CASE_INSENSITIVE);
  private static final int MIN_YEAR = 1500;
  private static final int MAX_YEAR = Year.now().getValue() + 10;
  private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{3,4})\\s*(\\?)?(?!\\d)");

  protected final NeoDb store;
  protected final DatasetSettings settings;
  private final Gazetteer distributionStandard;
  protected final ReferenceFactory refFactory;

  public InterpreterBase(DatasetSettings settings, ReferenceFactory refFactory, NeoDb store) {
    this.settings = settings;
    this.refFactory = refFactory;
    this.store = store;
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
  
  protected Reference setReference(VerbatimRecord v, Term refIdTerm, Consumer<String> refIdConsumer){
    Reference ref = null;
    if (v.hasTerm(refIdTerm)) {
      String rid = v.getRaw(refIdTerm);
      ref = refFactory.find(rid, null);
      if (ref == null) {
        LOG.debug("ReferenceID {} not existing but referred from {} in file {} line {}", rid, refIdTerm.prefixedName(), v.getFile(), v.fileLine());
        v.addIssue(Issue.REFERENCE_ID_INVALID);
      } else {
        refIdConsumer.accept(ref.getId());
      }
    }
    return ref;
  }

  /**
   * Sets a taxonomic note to usage namePhrase or a proper accordingTo reference if parsable
   */
  protected void setTaxonomicNote(NameUsageBase u, String taxNote, VerbatimRecord v) {
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

  protected void setAccordingTo(NameUsageBase u, String accordingTo, VerbatimRecord v) {
    Reference ref = buildReference(accordingTo, v);
    if (ref != null) {
      if (u.getAccordingToId() != null) {
        v.addIssue(Issue.ACCORDING_TO_CONFLICT);
      }
      u.setAccordingToId(ref.getId());
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

  protected List<VernacularName> interpretVernacular(VerbatimRecord rec, BiConsumer<VernacularName, VerbatimRecord> addReference,
                                                     Term name, Term translit, Term lang, Term sex, Term area, Term... countryTerms) {
    String vname = rec.get(name);
    if (vname != null) {
      VernacularName vn = new VernacularName();
      vn.setVerbatimKey(rec.getId());
      vn.setName(vname);
      
      if (translit != null) {
        vn.setLatin(rec.get(translit));
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
      vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(countryTerms)).orNull(Issue.VERNACULAR_COUNTRY_INVALID, rec));
  
      addReference.accept(vn, rec);
  
      return Lists.newArrayList(vn);
    }
    return Collections.emptyList();
  }
  
  protected List<Distribution> interpretDistribution(VerbatimRecord rec, BiConsumer<Distribution, VerbatimRecord> addReference,
                                                     Term tArea, Term tGazetteer, Term tStatus) {
    // require location
    if (rec.hasTerm(tArea)) {
      // which standard?
      Gazetteer gazetteer;
      if (distributionStandard != null) {
        gazetteer = distributionStandard;
      } else {
        gazetteer = parse(GazetteerParser.PARSER, rec.get(tGazetteer))
            .orElse(Gazetteer.TEXT, Issue.DISTRIBUTION_GAZETEER_INVALID, rec);
      }
      return createDistributions(gazetteer, rec.get(tArea), rec.get(tStatus), rec, addReference);
    }
    return Collections.emptyList();
  }
  
  private static Distribution createDistribution(VerbatimRecord rec, Gazetteer standard, String area, DistributionStatus status,
                                          BiConsumer<Distribution, VerbatimRecord> addReference) {
    Distribution d = new Distribution();
    d.setVerbatimKey(rec.getId());
    d.setGazetteer(standard);
    d.setArea(area);
    d.setStatus(status);
    addReference.accept(d, rec);
    return d;
  }
  
  protected static List<Distribution> createDistributions(@Nullable Gazetteer standard, final String locRaw, String statusRaw, VerbatimRecord rec,
                                                   BiConsumer<Distribution, VerbatimRecord> addReference) {
    if (locRaw != null) {

      final DistributionStatus status = parse(DistributionStatusParser.PARSER, statusRaw)
          .orElse(DistributionStatus.NATIVE, Issue.DISTRIBUTION_STATUS_INVALID, rec);

      if (standard == Gazetteer.TEXT) {
        return Lists.newArrayList( createDistribution(rec, Gazetteer.TEXT, locRaw, status, addReference) );
      
      } else {
        List<Distribution> distributions = new ArrayList<>();
        for (String loc : words(locRaw)) {
          // add gazetteer prefix if not yet included
          if (standard != null && loc.indexOf(':') < 0) {
            loc = standard.locationID(loc);
          }
          AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orNull(Issue.DISTRIBUTION_AREA_INVALID, rec);
          if (area != null) {
            // check if we have contradicting extracted a gazetteer
            if (standard != null && area.standard != Gazetteer.TEXT && area.standard != standard) {
              LOG.info("Area standard {} found in area {} different from explicitly given standard {} for {}",
                        area.standard, area.area, standard, rec);
            }
            distributions.add(createDistribution(rec, area.standard, area.area, status, addReference));
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
      words.add(m.group(0));
    }
    return words;
  }

  
  protected List<Media> interpretMedia(VerbatimRecord rec, BiConsumer<Media, VerbatimRecord> addReference,
                 Term type, Term url, Term link, Term license, Term creator, Term created, Term title, Term format) {
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

  private static boolean hasNoSpace(String x) {
    return x == null || !x.contains(" ");
  }
  
  private static String lowercaseEpithet(String epithet, IssueContainer issues) {
    if (epithet != null) {
      if (epithet.trim().contains(" ")) {
        issues.addIssue(Issue.MULTI_WORD_EPITHET);
        
      } else if (!epithet.equals(epithet.toLowerCase())) {
        issues.addIssue(Issue.UPPERCASE_EPITHET);
        return epithet.toLowerCase();
      }
    }
    return epithet;
  }
  
  private static void setDefaultNameType(Name n) {
    if (n.getCode() == NomCode.VIRUS) {
      n.setType(NameType.VIRUS);
    } else {
      n.setType(NameType.SCIENTIFIC);
    }
  }

  public Optional<ParsedNameUsage> interpretName(final boolean preferAtoms, final String id, final String vrank, final String sciname, final String authorship,
                                                 final String genus, final String infraGenus, final String species, final String infraspecies,
                                                 final String cultivar,
                                                 String nomCode, String nomStatus,
                                                 String link, String remarks, VerbatimRecord v) {
    // this can be wrong in some cases, e.g. in DwC records often scientificName and just a genus is given
    final boolean isAtomized = ObjectUtils.anyNonBlank(genus, infraGenus, species, infraspecies);
    final boolean useAtoms   = isAtomized && (preferAtoms || sciname == null);

    // parse rank & code as they improve name parsing
    Rank rank = SafeParser.parse(RankParser.PARSER, vrank).orElse(Rank.UNRANKED, Issue.RANK_INVALID, v);
    final NomCode code = SafeParser.parse(NomCodeParser.PARSER, nomCode).orElse((NomCode) settings.getEnum(Setting.NOMENCLATURAL_CODE), Issue.NOMENCLATURAL_CODE_INVALID, v);

    ParsedNameUsage pnu;

    // we can get the scientific name in various ways.
    // we prefer already atomized names as we want to trust humans more than machines
    if (useAtoms) {
      pnu = new ParsedNameUsage();
      Name atom = new Name();
      pnu.setName(atom);

      atom.setGenus(genus);
      atom.setInfragenericEpithet(infraGenus);
      atom.setSpecificEpithet(lowercaseEpithet(species, v));
      atom.setInfraspecificEpithet(lowercaseEpithet(infraspecies, v));
      atom.setCultivarEpithet(cultivar);
      atom.setRank(rank);
      atom.setCode(code);
      setDefaultNameType(atom);
      // populate uninomial instead of genus?
      if (!atom.isBinomial() && rank.isGenusOrSuprageneric() && atom.getGenus() != null && atom.getInfragenericEpithet() == null) {
        atom.setUninomial(atom.getGenus());
        atom.setGenus(null);
      }

      if (rank.otherOrUnranked()) {
        atom.setRank(RankUtils.inferRank(atom));
      }
      atom.rebuildScientificName();

      // parse the reconstructed name without authorship to detect name type and potential problems
      Optional<ParsedNameUsage> pnuFromAtom = NameParser.PARSER.parse(atom.getLabel(), rank, code, v);
      if (pnuFromAtom.isPresent()) {
        final Name pn = pnuFromAtom.get().getName();

        // check name type if its parsable - otherwise we should not use name atoms
        if (!pn.getType().isParsable()) {
          LOG.info("Atomized name {} appears to be of type {}. Use scientific name only", atom.getLabel(), pn.getType());
          pnu.setName(pn);
        } else if (pn.isParsed()) {
          // if parsed compare with original atoms
          if (
              !Objects.equals(atom.getUninomial(), pn.getUninomial()) ||
                  !Objects.equals(atom.getGenus(), pn.getGenus()) ||
                  !Objects.equals(atom.getInfragenericEpithet(), pn.getInfragenericEpithet()) ||
                  !Objects.equals(atom.getSpecificEpithet(), pn.getSpecificEpithet()) ||
                  !Objects.equals(atom.getInfraspecificEpithet(), pn.getInfraspecificEpithet())
          ) {
            LOG.warn("Parsed and given name atoms differ: [{}] vs [{}]", pn.getLabel(), atom.getLabel());
            v.addIssue(Issue.PARSED_NAME_DIFFERS);
          }
        }
      } else {
        // only really happens for blank strings
        LOG.info("No name given for {}", id);
        return Optional.empty();
      }

    } else if (sciname != null) {
      pnu = NameParser.PARSER.parse(sciname, rank, code, v).get();

    } else {
      LOG.info("No name given for {}", id);
      return Optional.empty();
    }

    // try to add an authorship if not yet there
    NameParser.PARSER.parseAuthorshipIntoName(pnu, authorship, v);

    // common basics
    pnu.getName().setId(id);
    pnu.getName().setVerbatimKey(v.getId());
    pnu.getName().setOrigin(Origin.SOURCE);
    pnu.getName().setLink(parse(UriParser.PARSER, link).orNull());
    pnu.getName().setRemarks(remarks);
    // applies default dataset code if we cannot find or parse any
    // Always make sure this happens BEFORE we update the canonical scientific name
    pnu.getName().setCode(code);

    // name status can be explicitly given or as part of the nom notes from the authorship
    // dont store the explicit name status, it only remains as verbatim and interpreted data
    // see https://github.com/CatalogueOfLife/backend/issues/760
    NomStatus status           = parse(NomStatusParser.PARSER, nomStatus).orNull(Issue.NOMENCLATURAL_STATUS_INVALID, v);
    NomStatus statusAuthorship = parse(NomStatusParser.PARSER, pnu.getName().getNomenclaturalNote()).orNull(Issue.NOMENCLATURAL_STATUS_INVALID, v);
    if (statusAuthorship != null) {
      v.addIssue(Issue.AUTHORSHIP_CONTAINS_NOMENCLATURAL_NOTE);
    }
    // both given? do they match up?
    if (status != null && statusAuthorship != null) {
      if (status != statusAuthorship && !status.isCompatible(statusAuthorship)) {
        v.addIssue(Issue.CONFLICTING_NOMENCLATURAL_STATUS);
      }
    }
    pnu.getName().setNomStatus(life.catalogue.api.util.ObjectUtils.coalesce(status, statusAuthorship));

    // assign best rank
    if (rank.notOtherOrUnranked() || pnu.getName().getRank() == null) {
      // TODO: check ACEF ranks...
      pnu.getName().setRank(rank);
    }

    // finally update the scientificName with the canonical form if we can
    pnu.getName().rebuildScientificName();

    return Optional.of(pnu);
  }

  public NeoUsage interpretUsage(ParsedNameUsage pnu, Term taxStatusTerm, TaxonomicStatus defaultStatus, VerbatimRecord v, Term... idTerms) {
    NeoUsage u;
    // a synonym by status?
    EnumNote<TaxonomicStatus> status = SafeParser.parse(TaxonomicStatusParser.PARSER, v.get(taxStatusTerm))
      .orElse(()->new EnumNote<>(defaultStatus, null), Issue.TAXONOMIC_STATUS_INVALID, v);
    if (status.val.isSynonym()) {
      u = NeoUsage.createSynonym(Origin.SOURCE, pnu.getName(), status.val);
    } else {
      u = NeoUsage.createTaxon(Origin.SOURCE, pnu.getName(), status.val);
      if (pnu.isExtinct()) {
        ((Taxon) u.usage).setExtinct(true);
      }
    }

    // shared usage props
    u.setId(v.getFirstRaw(idTerms));
    u.setVerbatimKey(v.getId());
    setTaxonomicNote(u.usage, pnu.getTaxonomicNote(), v);
    u.homotypic = TaxonomicStatusParser.isHomotypic(status);
    if (pnu.isExtinct()) {
      // flag this also for synonyms which cannot have the extinct flag
      v.addIssue(Issue.NAME_CONTAINS_EXTINCT_SYMBOL);
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

  protected void setLifezones(Taxon t, VerbatimRecord v, Term lifezone) {
    String raw = v.get(lifezone);
    if (raw != null) {
      for (String lzv : words(raw)) {
        Lifezone lz = parse(LifezoneParser.PARSER, lzv).orNull(Issue.LIFEZONE_INVALID, v);
        if (lz != null) {
          t.getLifezones().add(lz);
        }
      }
    }
  }
  
  protected static Integer parseYear(Term term, VerbatimRecord v) {
    return parseYear(v.get(term), v);
  }
  
  protected static Integer parseYear(String year, IssueContainer issues) {
    if (!StringUtils.isBlank(year)) {
      Matcher m = YEAR_PATTERN.matcher(year.trim());
      if (m.find()) {
        Integer y;
        if (m.group(2) != null) {
          // convert ? to a zero
          y = Integer.parseInt(m.group(1)+"0");
        } else {
          y = Integer.parseInt(m.group(1));
        }
        if (y < MIN_YEAR || y > MAX_YEAR) {
          issues.addIssue(Issue.UNLIKELY_YEAR);
        }
        return y;
      
      } else {
        issues.addIssue(Issue.UNPARSABLE_YEAR);
      }
    }
    return null;
  }

}
