package org.col.admin.task.importer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.ibm.icu.text.Transliterator;
import org.apache.commons.lang3.StringUtils;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.exception.InvalidNameException;
import org.col.api.model.Name;
import org.col.api.model.Reference;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;
import org.col.parser.BooleanParser;
import org.col.parser.DateParser;
import org.col.parser.UriParser;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDate;

import static org.col.parser.SafeParser.parse;

/**
 * Base interpreter providing common methods for both ACEF and DWC
 */
public class InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterBase.class);
  protected static final Splitter MULTIVAL = Splitter.on(CharMatcher.anyOf(";|,")).trimResults();
  private static final Transliterator transLatin = Transliterator.getInstance("Any-Latin");
  private static final Transliterator transAscii = Transliterator.getInstance("Latin-ASCII");

  protected final ReferenceStore refStore;

  public InterpreterBase(ReferenceStore refStore) {
    this.refStore = refStore;
  }

  protected String latinName(String name) {
    return transLatin.transform(name);
  }

  protected String asciiName(String name) {
    return transAscii.transform(latinName(name));
  }

  /**
   * Transliterates a vernacular name if its not yet existing
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
    return parse(DateParser.PARSER, t.verbatim.getTerm(term)).orNull(invalidIssue, t.issues);
  }

  protected URI uri(NeoTaxon t, Issue invalidIssue, Term ... term) {
    return parse(UriParser.PARSER, t.verbatim.getFirst(term)).orNull(invalidIssue, t.issues);
  }

  protected Boolean bool(NeoTaxon t, Issue invalidIssue, Term ... term) {
    return parse(BooleanParser.PARSER, t.verbatim.getFirst(term)).orNull(invalidIssue, t.issues);
  }

  protected Reference lookupReferenceTitleID(String id, String title) {
    // first try by id
    Reference r = refStore.refById(id);
    if (r == null && title != null) {
      // then try by title
      r = refStore.refByTitle(title);
      if (r == null) {
        // lastly create a new reference
        r = Reference.create();
        r.setId(id);
        r.setTitle(title);
        refStore.put(r);
      }
    }
    return r;
  }

  public void updateScientificName(String id, Name n) {
    try {
      n.updateScientificName();
      if (isInconsistent(id, n)) {
        n.addIssue(Issue.INCONSISTENT_NAME);
      }
    } catch (InvalidNameException e) {
      LOG.warn("Invalid atomised name found: {}", n);
      n.addIssue(Issue.INCONSISTENT_NAME);
    }
  }


  /**
   * Validates consistency of name properties. This method checks if the given rank matches
   * populated properties and available properties make sense together.
   */
  @VisibleForTesting
  protected static boolean isInconsistent(String id, Name n) {
    // only check for type scientific
    if (n.getType() == NameType.SCIENTIFIC) {
      final Rank rank = n.getRank();
      if (n.getUninomial() != null && (n.getGenus() != null || n.getInfragenericEpithet() != null
          || n.getSpecificEpithet() != null || n.getInfraspecificEpithet() != null)) {
        LOG.info("Uninomial with further epithets in name {}: {}", id, n.toStringComplete());
        return true;

      } else if (n.getGenus() == null && (n.getSpecificEpithet() != null || n.getInfragenericEpithet() != null)) {
        LOG.info("Missing genus in name {}: {}", id, n.toStringComplete());
        return true;

      } else if (n.getSpecificEpithet() == null && n.getInfraspecificEpithet() != null) {
        LOG.info("Missing specific epither in infraspecific name {}: {}", id, n.toStringComplete());
        return true;
      }

      // verify ranks
      if (rank.notOtherOrUnranked()) {
        if (rank.isGenusOrSuprageneric()) {
          if (n.getGenus() != null || n.getUninomial() == null) {
            LOG.info("Missing genus or uninomial for {} {}: {}", n.getRank(), id, n.toStringComplete());
            return true;
          }

        } else if (rank.isInfrageneric() && rank.isSupraspecific()) {
          if (n.getInfragenericEpithet() == null) {
            LOG.info("Missing infrageneric epithet for {} {}: {}", n.getRank(), id, n.toStringComplete());
            return true;
          }

          if (n.getSpecificEpithet() != null || n.getInfraspecificEpithet() != null) {
            LOG.info("Species or infraspecific epithet for {} {}: {}", n.getRank(), id, n.toStringComplete());
            return true;
          }

        } else if (rank.isSpeciesOrBelow()) {
          if (n.getSpecificEpithet() == null) {
            LOG.info("Missing specific epithet for {} {}: {}", n.getRank(), id, n.toStringComplete());
            return true;
          }

          if (!rank.isInfraspecific() && n.getInfraspecificEpithet() != null) {
            LOG.info("Infraspecific epithet found for {} {}: {}", n.getRank(), id, n.toStringComplete());
            return true;
          }
        }

        if (rank.isInfraspecific()) {
          if (n.getInfraspecificEpithet() == null) {
            LOG.info("Missing infraspecific epithet for {} {}: {}", n.getRank(), id, n.toStringComplete());
            return true;
          }
        }
      }
    }
    return false;
  }

}
