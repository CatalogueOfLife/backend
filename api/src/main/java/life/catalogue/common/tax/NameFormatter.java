package life.catalogue.common.tax;


import com.google.common.base.Joiner;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameUsageBase;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.UnicodeUtils;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 *
 */
public class NameFormatter {
  public static final char HYBRID_MARKER = '×';
  private static final String NOTHO_PREFIX = "notho";
  private static final Joiner AUTHORSHIP_JOINER = Joiner.on(", ").skipNulls();
  private static final String ITALICS_OPEN = "<i>";
  private static final String ITALICS_CLOSE = "</i>";
  private static final Pattern AL = Pattern.compile("^al\\.?$");

  private NameFormatter() {

  }

  /**
   * A full scientific name without authorship from the individual properties in its canonical form.
   * Subspecies are using the subsp rank marker unless a name is assigned to the zoological code.
   *
   * Uses name parts for parsed names, but the single scientificName field in case of unparsed names.
   */
  public static String scientificName(Name n, boolean html) {
    // make sure this is a parsed name, otherwise just return prebuilt name
    if (!n.isParsed()) {
      return n.getScientificName();
    }
    // https://github.com/gbif/portal-feedback/issues/640
    // final char transformations
    String name = buildScientificName(n, html).toString().trim();
    return UnicodeUtils.decompose(name);
  }

  /**
   * A full scientific name with authorship including potential namePhrases from the usage.
   * Otherwise identical to scientificName() method.
   *
   * Uses name parts for parsed names, but the single scientificName field in case of unparsed names.
   */
  public static String scientificNameAuthorship(NameUsageBase nu, boolean html) {
    // make sure this is a parsed name, otherwise just return prebuilt name
    StringBuilder sb;
    if (!nu.getName().isParsed()) {
      sb = new StringBuilder();
      sb.append(nu.getName().getScientificName());
      if (nu.getName().getAuthorship() != null) {
        sb.append(" ");
        sb.append(nu.getName().getAuthorship());
      }

    } else {
      sb = buildScientificName(nu.getName(), html);
      if (nu.getName().hasAuthorship()) {
        sb.append(" ");
        appendAuthorship(nu.getName(), sb);
      }
    }
    appendUsage(nu, sb, html);
    String name = sb.toString().trim();
    return UnicodeUtils.decompose(name);
  }

  /**
   * The full concatenated authorship for parsed names including the sanctioning author.
   */
  public static String authorship(Name n) {
    StringBuilder sb = new StringBuilder();
    appendAuthorship(n, sb);
    return sb.length() == 0 ? null : sb.toString();
  }

  private static void openItalics(StringBuilder sb) {
    sb.append(ITALICS_OPEN);
  }

  private static void closeItalics(StringBuilder sb) {
    sb.append(ITALICS_CLOSE);
  }

  private static void appendInItalics(StringBuilder sb, String x, boolean html) {
    if (html) {
      sb.append(ITALICS_OPEN)
        .append(x)
        .append(ITALICS_CLOSE);

    } else {
      sb.append(x);
    }
  }

  /**
   * build a scientific name without authorship from a parsed Name instance.
   * @param html                 add html markup
   */
  private static StringBuilder buildScientificName(Name n, boolean html) {
    StringBuilder sb = new StringBuilder();

    boolean candidateItalics = false;
    if (n.isCandidatus()) {
      sb.append("\"");
      if (html) {
        openItalics(sb);
        candidateItalics = true;
        // we turn off html here cause the entire name should be in italics!
        html = false;
      }
      sb.append("Candidatus ");
    }

    if (n.getUninomial() != null) {
      // higher rank names being just a uninomial!
      if (NamePart.GENERIC == n.getNotho()) {
        sb.append(HYBRID_MARKER)
          .append(" ");
      }
      appendInItalics(sb, n.getUninomial(), html);

    } else {
      // bi- or trinomials or infrageneric names
      if (n.getInfragenericEpithet() != null) {
        if ((isUnknown(n.getRank()) && n.getSpecificEpithet() == null) || (n.getRank() != null && n.getRank().isInfragenericStrictly())) {
          boolean showInfraGen = true;
          // the infrageneric is the terminal rank. Always show it and wrap it with its genus if requested
          if (n.getGenus() != null) {
            appendGenus(sb, n, html);
            sb.append(" ");
            // we show zoological infragenerics in brackets,
            // but use rank markers for botanical names (unless its no defined rank)
            if (NomCode.ZOOLOGICAL == n.getCode()) {
              sb.append("(");
              if (NamePart.INFRAGENERIC == n.getNotho()) {
                sb.append(HYBRID_MARKER)
                  .append(' ');
              }
              appendInItalics(sb, n.getInfragenericEpithet(), html);
              sb.append(")");
              showInfraGen = false;
            }
          }
          if (showInfraGen) {
            // If we know the rank we use explicit rank markers
            // this is how botanical infrageneric names are formed, see http://www.iapt-taxon.org/nomen/main.php?page=art21
            if (appendRankMarker(sb, n.getRank(), NamePart.INFRAGENERIC == n.getNotho())) {
              sb.append(' ');
            }
            appendInItalics(sb, n.getInfragenericEpithet(), html);
          }

        } else {
          if (n.getGenus() != null) {
            appendGenus(sb, n, html);
          }
          // additional subgenus shown for binomial. Always shown in brackets
          sb.append(" (");
          appendInItalics(sb, n.getInfragenericEpithet(), html);
          sb.append(")");
        }

      } else if (n.getGenus() != null) {
        appendGenus(sb, n, html);
      }

      if (n.getSpecificEpithet() == null) {
        if (n.getGenus() != null && n.getCultivarEpithet() == null) {
          if (n.getRank() != null && n.getRank().isSpeciesOrBelow()) {
            // no species epithet given, indetermined!
            if (n.getRank().isInfraspecific()) {
              // maybe we have an infraspecific epithet? force to show the rank marker
              appendInfraspecific(sb, n, true, html);
            } else {
              sb.append(" ");
              sb.append(n.getRank().getMarker());
            }
          }
        } else if (n.getInfraspecificEpithet() != null) {
          appendInfraspecific(sb, n, false, html);
        }

      } else {
        // species part
        sb.append(' ');
        if (NamePart.SPECIFIC == n.getNotho()) {
          sb.append(HYBRID_MARKER)
            .append(" ");
        }
        appendInItalics(sb, n.getSpecificEpithet(), html);

        if (n.getInfraspecificEpithet() == null) {
          // Indetermined infraspecies? Only show indet cultivar marker if no cultivar epithet exists
          if ( n.getRank() != null
            && n.getRank().isInfraspecific()
            && (NomCode.CULTIVARS != n.getRank().isRestrictedToCode() || n.getCultivarEpithet() == null)
          ) {
            // no infraspecific epitheton given, but rank below species. Indetermined!
            // use ssp. for subspecies in case of indetermined names
            if (n.getRank() == Rank.SUBSPECIES) {
              sb.append(" ssp.");
            } else {
              sb.append(' ');
              sb.append(n.getRank().getMarker());
            }
          }

        } else {
          // infraspecific part
          appendInfraspecific(sb, n, false, html);
        }
      }
    }

    // closing quotes for Candidatus names
    if (n.isCandidatus()) {
      if (candidateItalics) {
        closeItalics(sb);
      }
      sb.append("\"");
    }

    // add cultivar name
    if (n.getCultivarEpithet() != null) {
      if (Rank.CULTIVAR_GROUP == n.getRank()) {
        sb.append(" ")
          .append(n.getCultivarEpithet())
          .append(" Group");

      } else if (Rank.GREX == n.getRank()) {
        sb.append(" ")
          .append(n.getCultivarEpithet())
          .append(" gx");

      } else {
        sb.append(" '")
          .append(n.getCultivarEpithet())
          .append("'");
      }
    }

    // add nom status
    //if (n.getNomenclaturalNote() != null) {
    //  appendIfNotEmpty(sb, ", ")
    //    .append(n.getNomenclaturalNote());
    //}
    return sb;
  }

  private static StringBuilder appendInfraspecific(StringBuilder sb, Name n, boolean forceRankMarker, boolean html) {
    // infraspecific part
    sb.append(' ');
    if (NamePart.INFRASPECIFIC == n.getNotho()) {
      if (n.getRank() != null && isInfraspecificMarker(n.getRank())) {
        sb.append("notho");
      } else {
        sb.append(HYBRID_MARKER);
        sb.append(" ");
      }
    }
    // hide subsp. from zoological names
    if (forceRankMarker || isNotZoo(n.getCode()) || Rank.SUBSPECIES != n.getRank() || n.getNotho() != null) {
      if (appendRankMarker(sb, n.getRank(), NameFormatter::isInfraspecificMarker, false) && n.getInfraspecificEpithet() != null) {
        sb.append(' ');
      }
    }
    if (n.getInfraspecificEpithet() != null) {
      appendInItalics(sb, n.getInfraspecificEpithet(), html);
    }
    return sb;
  }

  private static StringBuilder appendIfNotEmpty(StringBuilder sb, String toAppend) {
    if (sb.length() > 0) {
      sb.append(toAppend);
    }
    return sb;
  }

  private static boolean isNotZoo(NomCode code) {
    return code != null && code != NomCode.ZOOLOGICAL;
  }

  private static boolean isUnknown(Rank r) {
    return r == null || r.otherOrUnranked();
  }

  private static boolean isInfraspecificMarker(Rank r) {
    return r.isInfraspecific() && !r.isUncomparable();
  }

  /**
   * @return true if rank marker was added
   */
  private static boolean appendRankMarker(StringBuilder sb, Rank rank, boolean nothoPrefix) {
    return appendRankMarker(sb, rank, null, nothoPrefix);
  }

  /**
   * @return true if rank marker was added
   */
  private static boolean appendRankMarker(StringBuilder sb, Rank rank, Predicate<Rank> ifRank, boolean nothoPrefix) {
    if (rank != null
      && rank.getMarker() != null
      && (ifRank == null || ifRank.test(rank))
    ) {
      if (nothoPrefix) {
        sb.append(NOTHO_PREFIX);
      }
      sb.append(rank.getMarker());
      return true;
    }
    return false;
  }

  private static StringBuilder appendGenus(StringBuilder sb, Name n, boolean html) {
    if (NamePart.GENERIC == n.getNotho()) {
      sb.append(HYBRID_MARKER)
        .append(" ");
    }
    appendInItalics(sb, n.getGenus(), html);
    return sb;
  }

  private static String joinAuthors(List<String> authors, boolean abbrevWithEtAl) {
    if (abbrevWithEtAl && authors.size() > 2) {
      return AUTHORSHIP_JOINER.join(authors.subList(0, 1)) + " et al.";

    } else if (authors.size() > 1) {
      String end;
      if (AL.matcher(authors.get(authors.size() - 1)).find()) {
        end = " et al.";
      } else {
        end = " & " + authors.get(authors.size() - 1);
      }
      return AUTHORSHIP_JOINER.join(authors.subList(0, authors.size() - 1)) + end;

    } else {
      return AUTHORSHIP_JOINER.join(authors);
    }
  }

  /**
   * Renders the authorship with ex authors and year
   *
   * @param sb StringBuilder to append to
   */
  private static void appendAuthorship(StringBuilder sb, Authorship auth, boolean includeYear) {
    if (auth != null && auth.exists()) {
      boolean authorsAppended = false;
      if (auth.hasExAuthors()) {
        sb.append(joinAuthors(auth.getExAuthors(), false));
        sb.append(" ex ");
        authorsAppended = true;
      }
      if (auth.hasAuthors()) {
        sb.append(joinAuthors(auth.getAuthors(), false));
        authorsAppended = true;
      }
      if (auth.getYear() != null && includeYear) {
        if (authorsAppended) {
          sb.append(", ");
        }
        sb.append(auth.getYear());
      }
    }
  }

  private static void appendUsage(NameUsageBase nu, StringBuilder sb, boolean html) {
    if (nu.getNamePhrase() != null) {
      sb.append(" ");
      sb.append(nu.getNamePhrase());
    }
    if (nu.getAccordingTo() != null) {
      sb.append(" ");
      if (html) {
        openItalics(sb);
      }
      sb.append("sensu");
      if (html) {
        closeItalics(sb);
      }
      sb.append(" ");
      sb.append(nu.getAccordingTo());
    }
  }

  private static void appendAuthorship(Name a, StringBuilder sb) {
    if (a.hasBasionymAuthorship()) {
      sb.append("(");
      appendAuthorship(sb, a.getBasionymAuthorship(), true);
      sb.append(")");
    }
    if (a.hasCombinationAuthorship()) {
      if (a.hasBasionymAuthorship()) {
        sb.append(" ");
      }
      appendAuthorship(sb, a.getCombinationAuthorship(), true);
      // Render sanctioning author via colon:
      // http://www.iapt-taxon.org/nomen/main.php?page=r50E
      //TODO: remove rendering of sanctioning author according to Paul Kirk!
      if (a.getSanctioningAuthor() != null) {
        sb.append(" : ");
        sb.append(a.getSanctioningAuthor());
      }
    }
  }

}
