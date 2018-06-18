package org.col.admin.importer;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.icu.text.Transliterator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.col.admin.importer.neo.ReferenceStore;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.exception.InvalidNameException;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.common.date.FuzzyDate;
import org.col.parser.*;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.parser.SafeParser.parse;

/**
 * Base interpreter providing common methods for both ACEF and DWC
 */
public class InterpreterBase {
  
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterBase.class);
  protected static final Splitter MULTIVAL = Splitter.on(CharMatcher.anyOf(";|,")).trimResults();
  private static final Transliterator transLatin = Transliterator.getInstance("Any-Latin");
  private static final Transliterator transAscii = Transliterator.getInstance("Latin-ASCII");
  
  protected final Dataset dataset;
  protected final ReferenceStore refStore;
  protected final ReferenceFactory refFactory;
  // TODO: replace with a map stored on disk or integrate in refstore
  private final Map<String, Reference> referenceByCitation;

  public InterpreterBase(Dataset dataset, ReferenceStore refStore, ReferenceFactory refFactory) {
    this.dataset = dataset;
    this.refStore = refStore;
    this.refFactory = refFactory;
    this.referenceByCitation = Maps.newHashMap();
  }

  protected String latinName(String name) {
    return transLatin.transform(name);
  }

  protected String asciiName(String name) {
    return transAscii.transform(latinName(name));
  }

  protected List<VernacularName> interpretVernacular(TermRecord rec, BiConsumer<VernacularName, TermRecord> addReferences, Term name, Term translit, Term lang, Term... countryTerms) {
    VernacularName vn = new VernacularName();
    vn.setVerbatimKey(rec.getKey());
    vn.setName(rec.get(name));
    if (StringUtils.isBlank(vn.getName())) {
      rec.addIssue(Issue.VERNACULAR_NAME_INVALID);
      return Collections.emptyList();
    }

    if (translit != null) {
      vn.setLatin(rec.get(translit));
    }
    if (lang != null) {
      vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(lang)).orNull());
    }
    vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(countryTerms)).orNull());

    addReferences.accept(vn, rec);

    if (StringUtils.isBlank(vn.getLatin())) {
      vn.setLatin(latinName(vn.getName()));
      rec.addIssue(Issue.VERNACULAR_NAME_TRANSLITERATED);
    }
    return Lists.newArrayList(vn);
  }

  protected LocalDate date(TermRecord v, VerbatimEntity ent, Issue invalidIssue, Term term) {
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
      return date.get().toLocalDate();
    }
    return null;
  }

  protected URI uri(TermRecord v, VerbatimEntity ent, Issue invalidIssue, Term... term) {
    return parse(UriParser.PARSER, v.getFirstRaw(term)).orNull(invalidIssue, v);
  }

  protected Boolean bool(TermRecord v, VerbatimEntity ent, Issue invalidIssue, Term... term) {
    return parse(BooleanParser.PARSER, v.getFirst(term)).orNull(invalidIssue, v);
  }

  protected Optional<Reference> lookupReference(String id, String citation, IssueContainer issues) {
    Reference r;
    // if we have an id make sure we have a record - even if its a duplicate
    if (id != null) {
      r = refStore.refById(id);
      if (r == null) {
        r = create(id, citation, issues);
      }
      return Optional.of(r);

    } else if (citation != null){
      // try to find matching reference based on citation
      if (referenceByCitation.containsKey(citation)) {
        r = referenceByCitation.get(citation);
      } else {
        r = create(id, citation, issues);
      }
      return Optional.of(r);

    }
    return Optional.empty();
  }

  private Reference create(String id, String citation, IssueContainer issues) {
    Reference r = refFactory.fromCitation(id, citation, issues);
    refStore.put(r);
    referenceByCitation.put(citation, r);
    return r;
  }

  public Optional<NameAccordingTo> interpretName(final String id, final String vrank, final String sciname, final String authorship,
                                       final String genus, final String infraGenus, final String species, final String infraspecies,
                                       String nomCode, String nomStatus, String link, String remarks, TermRecord v) {
    final boolean isAtomized = ObjectUtils.anyNotNull(genus, infraGenus, species, infraspecies);

    NameAccordingTo nat;

    Name atom = new Name();
    atom.setType(NameType.SCIENTIFIC);
    atom.setGenus(genus);
    atom.setInfragenericEpithet(infraGenus);
    atom.setSpecificEpithet(species);
    atom.setInfraspecificEpithet(infraspecies);

    // parse rank
    Rank rank = SafeParser.parse(RankParser.PARSER, vrank).orElse(Rank.UNRANKED, Issue.RANK_INVALID, v);
    atom.setRank(rank);

    // we can get the scientific name in various ways.
    // we parse all names from the scientificName + optional authorship
    // or use the atomized parts which we also use to validate the parsing result.
    if (sciname != null) {
      nat = NameParser.PARSER.parse(sciname, rank, v).get();

      // try to add an authorship if not yet there
      if (!Strings.isNullOrEmpty(authorship)) {
        ParsedName pnAuthorship = NameParser.PARSER.parseAuthorship(authorship).orElseGet(() -> {
          LOG.warn("Unparsable authorship {}", authorship);
          v.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
          // add the full, unparsed authorship in this case to not lose it
          ParsedName pn = new ParsedName();
          pn.getCombinationAuthorship().getAuthors().add(authorship);
          return pn;
        });

        // we did already parse an authorship from the scientificName string which does not match up?
        if (nat.getName().hasAuthorship()
            && !nat.getName().authorshipComplete().equalsIgnoreCase(pnAuthorship.authorshipComplete())
            ) {
          v.addIssue(Issue.INCONSISTENT_AUTHORSHIP);
          LOG.info("Different authorship [{}] found in dwc:scientificName=[{}] and dwc:scientificNameAuthorship=[{}]",
              nat.getName().authorshipComplete(), sciname, pnAuthorship.authorshipComplete());
        }
        nat.getName().setCombinationAuthorship(pnAuthorship.getCombinationAuthorship());
        nat.getName().setSanctioningAuthor(pnAuthorship.getSanctioningAuthor());
        nat.getName().setBasionymAuthorship(pnAuthorship.getBasionymAuthorship());
      }

    } else if (!isAtomized) {
      LOG.info("No name given for {}", id);
      return Optional.empty();

    } else {
      // parse the reconstructed name with authorship
      // cant use the atomized name just like that cause we would miss name type detection (virus,
      // hybrid, placeholder, garbage)
      nat = NameParser.PARSER.parse(atom.canonicalNameComplete() + " " + authorship, rank, v).get();
      // if parsed compare with original atoms
      if (nat.getName().isParsed()) {
        if (!Objects.equals(genus, nat.getName().getGenus()) ||
            !Objects.equals(infraGenus, nat.getName().getInfragenericEpithet()) ||
            !Objects.equals(species, nat.getName().getSpecificEpithet()) ||
            !Objects.equals(infraspecies, nat.getName().getInfraspecificEpithet())
        ) {
          LOG.warn("Parsed and given name atoms differ: [{}] vs [{}]", nat.getName().canonicalNameComplete(), atom.canonicalNameComplete());
          v.addIssue(Issue.PARSED_NAME_DIFFERS);
        }
      }
    }

    // common basics
    nat.getName().setId(id);
    nat.getName().setVerbatimKey(v.getKey());
    nat.getName().setOrigin(Origin.SOURCE);
    nat.getName().setSourceUrl(SafeParser.parse(UriParser.PARSER, link).orNull());
    nat.getName().setNomStatus(SafeParser.parse(NomStatusParser.PARSER, nomStatus).orElse(null, Issue.NOMENCLATURAL_STATUS_INVALID, v));
    // applies default dataset code if we cannot find or parse any
    // Always make sure this happens BEFORE we update the canonical scientific name
    nat.getName().setCode(SafeParser.parse(NomCodeParser.PARSER, nomCode).orElse(dataset.getCode(), Issue.NOMENCLATURAL_CODE_INVALID, v));
    nat.getName().setRemarks(remarks);

    // assign best rank
    if (rank.notOtherOrUnranked() || nat.getName().getRank() == null) {
      // TODO: check ACEF ranks...
      nat.getName().setRank(rank);
    }

    // finally update the scientificName with the canonical form if we can
    try {
      nat.getName().updateScientificName();
    } catch (InvalidNameException e) {
      LOG.info("Invalid atomised name found: {}", nat.getName());
      v.addIssue(Issue.INCONSISTENT_NAME);
    }

    return Optional.of(nat);
  }

}
