package org.col.admin.task.importer;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.ibm.icu.text.Transliterator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.reference.ReferenceFactory;
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

  /**
   * Transliterates a vernacular name if its not yet existing
   * 
   * @param t
   * @param vn
   */
  protected void addAndTransliterate(NeoTaxon t, VernacularName vn) {
    if (StringUtils.isBlank(vn.getName())) {
      // vernacular names required
      t.addIssue(Issue.VERNACULAR_NAME_INVALID);
    } else {
      if (StringUtils.isBlank(vn.getLatin()) && !StringUtils.isBlank(vn.getName())) {
        vn.setLatin(latinName(vn.getName()));
        t.addIssue(Issue.VERNACULAR_NAME_TRANSLITERATED);
      }
      t.vernacularNames.add(vn);
    }
  }

  protected LocalDate date(NeoTaxon t, Issue invalidIssue, Term term) {
    Optional<FuzzyDate> date;
    try {
      date = DateParser.PARSER.parse(t.verbatim.getTerm(term));
    } catch (UnparsableException e) {
      t.addIssue(invalidIssue);
      return null;
    }
    if (date.isPresent()) {
      if (date.get().isFuzzyDate()) {
        t.addIssue(Issue.PARTIAL_DATE);
      }
      return date.get().toLocalDate();
    }
    return null;
  }

  protected URI uri(NeoTaxon t, Issue invalidIssue, Term... term) {
    return parse(UriParser.PARSER, t.verbatim.getFirst(term)).orNull(invalidIssue, t.taxon.getIssues());
  }

  protected Boolean bool(NeoTaxon t, Issue invalidIssue, Term... term) {
    return parse(BooleanParser.PARSER, t.verbatim.getFirst(term)).orNull(invalidIssue, t.taxon.getIssues());
  }

  protected Optional<Reference> lookupReference(String id, String citation) {
    Reference r;
    // if we have an id make sure we have a record - even if its a duplicate
    if (id != null) {
      r = refStore.refById(id);
      if (r == null) {
        r = create(id, citation);
      }
      return Optional.of(r);

    } else if (citation != null){
      // try to find matching reference based on citation
      if (referenceByCitation.containsKey(citation)) {
        r = referenceByCitation.get(citation);
      } else {
        r = create(id, citation);
      }
      return Optional.of(r);

    }
    return Optional.empty();
  }

  private Reference create(String id, String citation) {
    Reference r = refFactory.fromCitation(id, citation);
    refStore.put(r);
    referenceByCitation.put(citation, r);
    return r;
  }

  public NameAccordingTo interpretName(final String id, final String vrank, final String sciname, final String authorship,
                                       final String genus, final String infraGenus, final String species, final String infraspecies,
                                       String nomCode, String nomStatus, String link, String remarks) {
    final Set<Issue> issues = EnumSet.noneOf(Issue.class);
    final boolean isAtomized = ObjectUtils.anyNotNull(genus, infraGenus, species, infraspecies);

    NameAccordingTo nat;

    Name atom = new Name();
    atom.setType(NameType.SCIENTIFIC);
    atom.setGenus(genus);
    atom.setInfragenericEpithet(infraGenus);
    atom.setSpecificEpithet(species);
    atom.setInfraspecificEpithet(infraspecies);

    // parse rank
    Rank rank = SafeParser.parse(RankParser.PARSER, vrank).orElse(Rank.UNRANKED, Issue.RANK_INVALID, issues);
    atom.setRank(rank);

    // we can get the scientific name in various ways.
    // we parse all names from the scientificName + optional authorship
    // or use the atomized parts which we also use to validate the parsing result.
    if (sciname != null) {
      nat = NameParser.PARSER.parse(sciname, rank).get();
      // try to add an authorship if not yet there
      if (!Strings.isNullOrEmpty(authorship)) {
        ParsedName pnAuthorship = NameParser.PARSER.parseAuthorship(authorship).orElseGet(() -> {
          LOG.warn("Unparsable authorship {}", authorship);
          nat.getName().addIssue(Issue.UNPARSABLE_AUTHORSHIP);
          // add the full, unparsed authorship in this case to not lose it
          ParsedName pn = new ParsedName();
          pn.getCombinationAuthorship().getAuthors().add(authorship);
          return pn;
        });

        // we did already parse an authorship from the scientificName string which does not match up?
        if (nat.getName().hasAuthorship()
            && !nat.getName().authorshipComplete().equalsIgnoreCase(pnAuthorship.authorshipComplete())
            ) {
          nat.getName().addIssue(Issue.INCONSISTENT_AUTHORSHIP);
          LOG.info("Different authorship [{}] found in dwc:scientificName=[{}] and dwc:scientificNameAuthorship=[{}]",
              nat.getName().authorshipComplete(), sciname, pnAuthorship.authorshipComplete());
        }
        nat.getName().setCombinationAuthorship(pnAuthorship.getCombinationAuthorship());
        nat.getName().setSanctioningAuthor(pnAuthorship.getSanctioningAuthor());
        nat.getName().setBasionymAuthorship(pnAuthorship.getBasionymAuthorship());
      }

    } else if (!isAtomized) {
      LOG.warn("No name given for {}", id);
      return null;

    } else {
      // parse the reconstructed name with authorship
      // cant use the atomized name just like that cause we would miss name type detection (virus,
      // hybrid, placeholder, garbage)
      nat = NameParser.PARSER.parse(atom.canonicalNameComplete() + " " + authorship, rank).get();
      // if parsed compare with original atoms
      if (nat.getName().isParsed()) {
        if (!Objects.equals(genus, nat.getName().getGenus())
            || !Objects.equals(infraGenus, nat.getName().getInfragenericEpithet())
            || !Objects.equals(species, nat.getName().getSpecificEpithet())
            || !Objects.equals(infraspecies, nat.getName().getInfraspecificEpithet())
            ) {
          LOG.warn("Parsed and given name atoms differ: [{}] vs [{}]", nat.getName().canonicalNameComplete(), atom.canonicalNameComplete());
          nat.getName().addIssue(Issue.PARSED_NAME_DIFFERS);
        }
      }
    }

    // common basics
    nat.getName().setId(id);
    nat.getName().setOrigin(Origin.SOURCE);
    nat.getName().setSourceUrl(SafeParser.parse(UriParser.PARSER, link).orNull());
    nat.getName().setNomStatus(SafeParser.parse(NomStatusParser.PARSER, nomStatus).orElse(null,
        Issue.NOMENCLATURAL_STATUS_INVALID, nat.getName().getIssues()));
    // applies default dataset code if we cannot find or parse any
    // Always make sure this happens BEFORE we update the canonical scientific name
    nat.getName().setCode(SafeParser.parse(NomCodeParser.PARSER, nomCode).orElse(dataset.getCode(),
        Issue.NOMENCLATURAL_CODE_INVALID, nat.getName().getIssues()));
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
      nat.getName().addIssue(Issue.INCONSISTENT_NAME);
    }
    nat.getName().getIssues().addAll(issues);

    return nat;
  }

}
