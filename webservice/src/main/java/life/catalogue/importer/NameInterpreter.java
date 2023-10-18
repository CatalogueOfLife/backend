package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.parser.SafeParser.parse;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Generic interpreter for name instances dealing with parsing and atomic inputs.
 * Expected to be used by all import interpreters as well as other inputs for names, e.g. matching.
 */
public class NameInterpreter {

  private static final Logger LOG = LoggerFactory.getLogger(NameInterpreter.class);

  protected final DatasetSettings settings;
  private final boolean preferAtoms;

  /**
   * Used settings for interpreting a name:
   * Setting.EPITHET_ADD_HYPHEN
   * Setting.NOMENCLATURAL_CODE
   */
  public NameInterpreter(DatasetSettings settings, boolean preferAtomsDefault) {
    this.settings = settings;
    preferAtoms = settings.getBoolDefault(Setting.PREFER_NAME_ATOMS, preferAtomsDefault);
  }

  public Optional<ParsedNameUsage> interpret(SimpleName sn, IssueContainer issues) {
    return interpret(false,
      sn.getId(), sn.getCode(), sn.getRank(), sn.getName(), sn.getAuthorship(), null,
      null, null, null, null, null, null,
      null, null, null, null, issues
    );
  }

  public Optional<ParsedNameUsage> interpret(final String id, String vrank, final String sciname,
                                             final String authorship, final String publishedInYear,
                                             final String uninomial, final String genus, final String infraGenus, final String species, String infraspecies,
                                             final String cultivar,
                                             String nomCode, String nomStatus,
                                             String link, String remarks, String identifiers, VerbatimRecord v) {
    return interpret(id, vrank, Rank.UNRANKED, sciname, authorship, publishedInYear,
      uninomial, genus, infraGenus, species, infraspecies, cultivar, nomCode, nomStatus, link, remarks, identifiers, v
    );
  }

  public Optional<ParsedNameUsage> interpret(final String id, String vrank, Rank defaultRank, final String sciname,
                                             final String authorship, final String publishedInYear,
                                             final String uninomial, final String genus, final String infraGenus, final String species, String infraspecies,
                                             final String cultivar,
                                             String nomCode, String nomStatus,
                                             String link, String remarks, String identifiers, VerbatimRecord v) {
    // parse rank & code as they improve name parsing
    final NomCode code = SafeParser.parse(NomCodeParser.PARSER, nomCode).orNull(Issue.NOMENCLATURAL_CODE_INVALID, v);

    // sometimes a rank marker is given as part of the epithet
    if (vrank == null && infraspecies != null) {
      Pattern rankMarker = Pattern.compile("^((?:var|f|subsp)[. ])");
      var m = rankMarker.matcher(infraspecies);
      if (m.find()) {
        vrank = m.group(1).trim();
        infraspecies = m.replaceFirst("").trim();
      }
    }
    Rank rank = ObjectUtils.coalesce(defaultRank, Rank.UNRANKED);
    // we only parse ranks given with more than one char. c or g alone can be very ambiguous, see https://github.com/CatalogueOfLife/data/issues/302
    if (vrank != null && (vrank.length()>1 || vrank.equals("f") || vrank.equals("v"))) {
      try {
        // use advanced rank parser that takes the code into account!
        var parsedRank = RankParser.PARSER.parse(code, vrank);
        if (parsedRank.isPresent()) {
          rank = parsedRank.get();
        }
      } catch (UnparsableException e) {
        v.addIssue(Issue.RANK_INVALID);
        rank = Rank.OTHER;
      }
    } else if (vrank != null) {
      v.addIssue(Issue.RANK_INVALID);
      rank = Rank.OTHER;
    }

    var opt = interpret(vrank==null,
      id, code, rank, sciname, authorship, publishedInYear,
      uninomial, genus, infraGenus, species, infraspecies, cultivar,
      nomStatus, link, remarks, identifiers, v
    );
    // keep the verbatim id
    opt.ifPresent(parsedNameUsage -> parsedNameUsage.getName().setVerbatimKey(v.getId()));
    return opt;
  }

  public Optional<ParsedNameUsage> interpret(final boolean allowToInferRank,
                                             final String id, NomCode code, Rank rank, final String sciname, final String authorship, final String publishedInYear,
                                             final String uninomial, final String genus, final String infraGenus, final String species, String infraspecies, final String cultivar,
                                             String nomStatus, String link, String remarks, String identifiers, IssueContainer issues) {
    try {
      // default code & rank
      code = ObjectUtils.coalesce(code, settings.getEnum(Setting.NOMENCLATURAL_CODE));
      rank = ObjectUtils.coalesce(rank, Rank.UNRANKED);

      // this can be wrong in some cases, e.g. in DwC records often scientificName and just a genus is given
      final boolean useAtoms;
      if (preferAtoms || StringUtils.isBlank(sciname)) {
        if (rank.isInfragenericStrictly()) {
          // require uninomial or infragenus
          useAtoms = ObjectUtils.anyNonBlank(uninomial, infraGenus);
        } else if (rank.isSupraspecific()) {
          // require uninomial or genus
          useAtoms = ObjectUtils.anyNonBlank(uninomial, genus);
        } else if (rank.isInfraspecific()) {
          // require genus, species and one lower level epithet
          useAtoms = ObjectUtils.allNonBlank(genus, species) && ObjectUtils.anyNonBlank(infraspecies, cultivar);
        } else if (rank.isSpeciesOrBelow()) {
          // require genus and species
          useAtoms = ObjectUtils.allNonBlank(genus, species);
        } else {
          useAtoms = ObjectUtils.anyNonBlank(uninomial, genus, infraGenus, species, infraspecies);
        }
      } else {
        useAtoms = false;
      }

      // we can get the scientific name in various ways.
      // we prefer already atomized names as we want to trust humans more than machines
      ParsedNameUsage pnu;
      if (useAtoms) {
        pnu = new ParsedNameUsage();
        Name atom = new Name();
        atom.setRank(rank);
        atom.setCode(code);
        setDefaultNameType(atom);
        pnu.setName(atom);

        set(pnu, atom::setUninomial, uninomial);
        set(pnu, atom::setGenus, genus);
        set(pnu, atom::setInfragenericEpithet, infraGenus);
        set(pnu, atom::setSpecificEpithet, sanitizeEpithet(species, issues));
        set(pnu, atom::setInfraspecificEpithet, sanitizeEpithet(infraspecies, issues));
        set(pnu, atom::setCultivarEpithet, cultivar);

        // misplaced uninomial in genus field
        if (!atom.isBinomial() && rank.isGenusOrSuprageneric() && atom.getGenus() != null && atom.getInfragenericEpithet() == null) {
          if (atom.getUninomial() == null) {
            atom.setUninomial(atom.getGenus());
          } else if (!atom.getUninomial().equals(atom.getGenus())) {
            issues.addIssue(Issue.INCONSISTENT_NAME);
          }
          atom.setGenus(null); // ignore genus if
        }
        // misplaced infrageneric in uninomial field
        if (rank.isInfragenericStrictly() && atom.getUninomial() != null) {
          if (atom.getInfragenericEpithet() == null) {
            atom.setInfragenericEpithet(atom.getUninomial()); // swap
          } else if (!atom.getUninomial().equals(atom.getInfragenericEpithet())) {
            issues.addIssue(Issue.INCONSISTENT_NAME);
          }
          atom.setUninomial(null); // no uninomial for infragenerics
        }
        atom.rebuildScientificName();

        // parse the reconstructed name without authorship to detect name type and potential problems
        Optional<ParsedNameUsage> pnuFromAtom = NameParser.PARSER.parse(atom.getLabel(), rank, code, issues);
        if (pnuFromAtom.isPresent()) {
          final var atomPNU = pnuFromAtom.get();
          final Name atomN = atomPNU.getName();

          // check name type if its parsable - otherwise we should not use name atoms
          if (!atomN.getType().isParsable()) {
            LOG.info("Atomized name {} appears to be of type {}. Use scientific name only", atom.getLabel(), atomN.getType());
            pnu.setName(atomN);
          } else if (atomN.isParsed()) {
            if (atomPNU.isDoubtful()) { // we might found brackets in the parsed genus
              pnu.setDoubtful(true);
              atom.setGenus(atomN.getGenus());
              atom.rebuildScientificName();
            }
            // if parsed compare with original atoms
            if (
                !Objects.equals(atom.getUninomial(), atomN.getUninomial()) ||
                    !Objects.equals(atom.getGenus(), atomN.getGenus()) ||
                    !Objects.equals(atom.getInfragenericEpithet(), atomN.getInfragenericEpithet()) ||
                    !Objects.equals(atom.getSpecificEpithet(), atomN.getSpecificEpithet()) ||
                    !Objects.equals(atom.getInfraspecificEpithet(), atomN.getInfraspecificEpithet())
            ) {
              LOG.warn("Parsed and given name atoms differ: [{}] vs [{}]", atomN.getLabel(), atom.getLabel());
              issues.addIssue(Issue.PARSED_NAME_DIFFERS);
            }
          }
        } else {
          // only really happens for blank strings
          LOG.info("No name given for {}", id);
          return Optional.empty();
        }

      } else if (StringUtils.isNotBlank(sciname)) {
        // be careful, this infers ranks from the name!
        pnu = NameParser.PARSER.parse(sciname, rank, code, issues).get();

      } else {
        LOG.info("No name given for {}", id);
        return Optional.empty();
      }

      // assign best rank
      // we potentially have an explicit one and one coming from the name parser that does inferal based on rank markers and suffices
      // we dont want to infer uninomials by their name endings - it works in most cases, but the few errors we get are really bad
      // see https://github.com/CatalogueOfLife/data/issues/438
      if (allowToInferRank && rank.otherOrUnranked()) {
        // we can infer the rank a little but be careful
        Rank inferred;
        if (pnu.getName().getRank() != null && pnu.getName().getRank().notOtherOrUnranked()) {
          // might be inferred already by the parser
          inferred = pnu.getName().getRank();
        } else {
          inferred = RankUtils.inferRank(pnu.getName());
        }
        // we ignore inferred ranks for uninomials above genera as these are suffix based
        // infrageneric names for plants mostly contain explicit rank markers, so we keep those
        if (!inferred.isGenusOrSuprageneric()) {
          rank = inferred;
        }
      }
      // finally use it
      pnu.getName().setRank(rank);

      // try to add an authorship if not yet there
      NameParser.PARSER.parseAuthorshipIntoName(pnu, authorship, issues);
      // populate name published in through various channels and verify its a real nomenclatural date
      if (publishedInYear != null){
        Integer year = InterpreterBase.parseNomenYear(publishedInYear, issues);
        if (year != null) {
          pnu.getName().setPublishedInYear(year);
        }
      }
      if (pnu.getName().getCombinationAuthorship() != null && pnu.getName().getCombinationAuthorship().getYear() != null){
        Integer year = InterpreterBase.parseNomenYear(pnu.getName().getCombinationAuthorship().getYear(), issues);
        if (year != null) {
          if (pnu.getName().getPublishedInYear() == null) {
            pnu.getName().setPublishedInYear(year);
          } else if (!pnu.getName().getPublishedInYear().equals(year)) {
            issues.addIssue(Issue.PUBLISHED_YEAR_CONFLICT);
          }
        }
      }

      // common basics
      pnu.getName().setId(id);
      pnu.getName().setOrigin(Origin.SOURCE);
      pnu.getName().setLink(parse(UriParser.PARSER, link).orNull());
      pnu.getName().setRemarks(remarks);
      // applies default dataset code if we cannot find or parse any
      // Always make sure this happens BEFORE we update the canonical scientific name
      if (code != null) {
        pnu.getName().setCode(code);
      }

      // name status can be explicitly given or as part of the nom notes from the authorship
      // dont store the explicit name status, it only remains as verbatim and interpreted data
      // see https://github.com/CatalogueOfLife/backend/issues/760
      NomStatus status           = parse(NomStatusParser.PARSER, nomStatus).orNull(Issue.NOMENCLATURAL_STATUS_INVALID, issues);
      NomStatus statusAuthorship = parse(NomStatusParser.PARSER, pnu.getName().getNomenclaturalNote()).orNull(Issue.NOMENCLATURAL_STATUS_INVALID, issues);
      if (statusAuthorship != null) {
        issues.addIssue(Issue.AUTHORSHIP_CONTAINS_NOMENCLATURAL_NOTE);
      }
      // both given? do they match up?
      if (status != null && statusAuthorship != null) {
        if (status != statusAuthorship && !status.isCompatible(statusAuthorship)) {
          issues.addIssue(Issue.CONFLICTING_NOMENCLATURAL_STATUS);
        }
      }
      pnu.getName().setNomStatus(ObjectUtils.coalesce(status, statusAuthorship));
      if (nomStatus != null && (pnu.getName().getNomStatus() == null || !nomStatus.trim().equalsIgnoreCase(pnu.getName().getNomStatus().name()))) {
        // add raw status to remarks
        pnu.getName().addRemarks(nomStatus);
      }
      pnu.getName().setIdentifier(InterpreterBase.interpretIdentifiers(identifiers, null, issues));

      // finally update the scientificName with the canonical form if we can
      pnu.getName().rebuildScientificName();

      // look for irregularities and flag issues
      if (pnu.getName().hasAuthorship()) {
        verifyNomenYear(pnu.getName().getCombinationAuthorship(), issues);
        verifyNomenYear(pnu.getName().getBasionymAuthorship(), issues);
      }

      return Optional.of(pnu);

    } catch (InterruptedException e) {
      // interpreters are free to throw the runtime equivalent
      throw new InterruptedRuntimeException(e.getMessage());
    }
  }

  private static void verifyNomenYear(Authorship authorship, IssueContainer issues) {
    InterpreterBase.parseNomenYear(authorship.getYear(), issues);
  }

  private static void set(ParsedNameUsage pnu, Consumer<String> setter, String epithet) {
    var epi = new ExtinctName(epithet);
    setter.accept(epi.name);
    if (epi.extinct) {
      pnu.setExtinct(true);
    }
  }


  protected boolean requireTerm(VerbatimRecord v, Term term, Issue notExistingIssue){
    if (!v.hasTerm(term)) {
      v.addIssue(notExistingIssue);
      return false;
    }
    return true;
  }

  private String sanitizeEpithet(String epithet, IssueContainer issues) {
    if (epithet != null && !epithet.equals(epithet.toLowerCase())) {
      issues.addIssue(Issue.UPPERCASE_EPITHET);
      epithet = epithet.trim().toLowerCase();
    } else {
      epithet = trimToNull(epithet);
    }
    if (epithet != null && settings.isEnabled(Setting.EPITHET_ADD_HYPHEN)) {
      epithet = epithet.replaceAll("\\s+", "-");
      issues.addIssue(Issue.MULTI_WORD_EPITHET);
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
}
