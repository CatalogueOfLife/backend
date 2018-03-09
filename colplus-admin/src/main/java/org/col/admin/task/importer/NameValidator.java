package org.col.admin.task.importer;

import com.google.common.annotations.VisibleForTesting;
import org.col.api.model.Name;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 */
public class NameValidator {
  private static final Logger LOG = LoggerFactory.getLogger(NameValidator.class);
  private static final Pattern WHITE = Pattern.compile("\\s");
  @VisibleForTesting
  static final Pattern NON_LETTER = Pattern.compile("[^a-z-]", Pattern.CASE_INSENSITIVE);

  /**
   * Validates consistency of name properties adding issues to the name if found. 
   * This method checks if the given rank matches
   * populated properties and available properties make sense together.
   * @return true if any issue have been added
   */
  public static boolean flagIssues(Name n) {
    // only check for type scientific which is parsable
    if (n.getType() != NameType.SCIENTIFIC) {
      return false;
    }

    final Set<Issue> issues = EnumSet.noneOf(Issue.class);
    final Rank rank = n.getRank();
    if (n.getUninomial() != null && (n.getGenus() != null || n.getInfragenericEpithet() != null
        || n.getSpecificEpithet() != null || n.getInfraspecificEpithet() != null)) {
      LOG.info("Uninomial with further epithets in name {}", n.toStringComplete());
      issues.add(Issue.INCONSISTENT_NAME);

    } else if (n.getGenus() == null && (n.getSpecificEpithet() != null || n.getInfragenericEpithet() != null)) {
      LOG.info("Missing genus in name {}", n.toStringComplete());
      issues.add(Issue.INCONSISTENT_NAME);

    } else if (n.getSpecificEpithet() == null && n.getInfraspecificEpithet() != null) {
      LOG.info("Missing specific epither in infraspecific name {}", n.toStringComplete());
      issues.add(Issue.INCONSISTENT_NAME);
    }

    // verify epithets
    for (String epithet : n.nameParts()) {
      // no whitespace
      if (WHITE.matcher(epithet).find()) {
        LOG.info("Name part contains whitespace {}", n.toStringComplete());
        issues.add(Issue.UNUSUAL_CHARACTERS);
      }
      // non ascii chars
      if (NON_LETTER.matcher(epithet).find()) {
        LOG.info("Name part contains non ASCII letters {}", n.toStringComplete());
        issues.add(Issue.UNUSUAL_CHARACTERS);
      }
    }

    // verify ranks
    if (rank.notOtherOrUnranked()) {
      if (rank.isGenusOrSuprageneric()) {
        if (n.getGenus() != null || n.getUninomial() == null) {
          LOG.info("Missing genus or uninomial for {} {}", n.getRank(), n.toStringComplete());
          issues.add(Issue.INCONSISTENT_NAME);
        }

      } else if (rank.isInfrageneric() && rank.isSupraspecific()) {
        if (n.getInfragenericEpithet() == null) {
          LOG.info("Missing infrageneric epithet for {} {}", n.getRank(), n.toStringComplete());
          issues.add(Issue.INCONSISTENT_NAME);
        }

        if (n.getSpecificEpithet() != null || n.getInfraspecificEpithet() != null) {
          LOG.info("Species or infraspecific epithet for {} {}", n.getRank(), n.toStringComplete());
          issues.add(Issue.INCONSISTENT_NAME);
        }

      } else if (rank.isSpeciesOrBelow()) {
        if (n.getSpecificEpithet() == null) {
          LOG.info("Missing specific epithet for {} {}", n.getRank(), n.toStringComplete());
          issues.add(Issue.INCONSISTENT_NAME);
        }

        if (!rank.isInfraspecific() && n.getInfraspecificEpithet() != null) {
          LOG.info("Infraspecific epithet found for {} {}", n.getRank(), n.toStringComplete());
          issues.add(Issue.INCONSISTENT_NAME);
        }
      }

      if (rank.isInfraspecific()) {
        if (n.getInfraspecificEpithet() == null) {
          LOG.info("Missing infraspecific epithet for {} {}", n.getRank(), n.toStringComplete());
          issues.add(Issue.INCONSISTENT_NAME);
        }
      }
    }

    if (issues.isEmpty()) {
      return false;
    } else {
      n.getIssues().addAll(issues);
      return true;
    }
  }
}
