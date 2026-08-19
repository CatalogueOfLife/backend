package life.catalogue.common.tax;


import life.catalogue.api.model.FormattableName;

import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.UnicodeUtils;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;

import static org.gbif.nameparser.util.NameFormatter.appendAuthorship;
/**
 * Formatting FormattableName instances to plain text and html labels.
 * See also the NameFormatter in the name parser package which formats ParsedName instances, but does similar.
 */
public class NameFormatter {
  public static final char HYBRID_MARKER = '×';
  private static final String NOTHO_PREFIX = "notho";
  private static final Joiner AUTHORSHIP_JOINER = Joiner.on(", ").skipNulls();
  private static final Pattern AL = Pattern.compile("^al\\.?$");
  // an unparsed/phrase portion that itself starts with a species marker ("species 1", "sp. 3"), so
  // the synthetic "sp." rank marker must not be prepended a second time ("Xyris sp. species 1")
  private static final Pattern UNPARSED_SPECIES_MARKER =
      Pattern.compile("^(?:sp|spec|species)\\b", Pattern.CASE_INSENSITIVE);
  private static Pattern RANK_MATCHER = Pattern.compile("^(.+[a-z]) ((?:notho)?(?:infra|super|sub)?(?:gx|natio|morph|klepton|lusus|strain|chemoform|(?:subsp|f\\. ?sp|[a-z]{1,4})\\.|[a-z]{3,6}var\\.?))( [a-z][^ ]*?)?( .+)?$");
  //private static Pattern RANK_MATCHER = Pattern.compile("^(.+[a-z]) ((?:notho|infra)?(?:gx|natio|morph|[a-z]{3,6}var\\.?|chemoform|f\\. ?sp\\.|strain|[a-z]{1,7}\\.))( [a-z][^ ]*?)?( .+)?$");
  // matches only uninomials or binomials without any authorship
  private static String EPITHET = "[a-z0-9ïëöüäåéèčáàæœ-]+";
  @VisibleForTesting
  static Pattern LINNEAN_NAME_NO_AUTHOR = Pattern.compile("^(?:× )?[A-ZÆŒ]"+EPITHET           // (notho)genus
                                                          + "(?: \\([A-ZÆŒ]"+EPITHET+"\\))?"  // infrageneric
                                                          + "(?: (?:× )?"+EPITHET             // (notho)species
                                                              +"(?: (?:× )?"+EPITHET+")?"     // (notho)subspecies
                                                          + ")?$");

  private NameFormatter() {

  }

  /**
   * A full scientific name without authorship from the individual properties in its canonical form.
   * Subspecies are using the subsp rank marker unless a name is assigned to the zoological code.
   *
   * Uses name parts for parsed names, but the single scientificName field in case of unparsed names.
   */
  public static String scientificName(FormattableName n) {
    // make sure this is a parsed name, otherwise just return prebuilt name
    if (!n.isParsed()) {
      return n.getScientificName();
    }
    // https://github.com/gbif/portal-feedback/issues/640
    // final char transformations
    String name = buildScientificName(n).toString().trim();
    return UnicodeUtils.decompose(name);
  }

  /**
   * The full concatenated authorship for parsed names including the sanctioning author.
   */
  public static String authorship(FormattableName n) {
    return authorship(n, true);
  }

  /**
   * The full concatenated authorship for parsed names including the sanctioning author.
   */
  public static String authorship(FormattableName n, boolean includeNotes) {
    StringBuilder sb = new StringBuilder();
    // delegate the basionym/combination/sanctioning (incl. imprint year) rendering to the shared
    // parser formatter so it stays in sync - FormattableName is a CombinedAuthorshipIF
    appendAuthorship(sb, n, true, n.getCode());
    if (includeNotes && n.getNomenclaturalNote() != null) {
      if (n.hasAuthorship()) {
        sb.append(" ");
      }
      sb.append(n.getNomenclaturalNote());
    }
    return sb.length() == 0 ? null : sb.toString();
  }

  /**
   * Builds a canonical name without authorship, rank or hybrid markers.
   * It will be strictly a uni-, bi- or trinomial without any further additions apart from an optional cultivar epithet in which case
   * the result can be a 4-parted name.
   *
   * Infrageneric names will be made uninomials without its embracing genus.
   *
   * If the name is inDetermined or unparsed the original scientificName will be returned.
   */
  public static String canonicalName(FormattableName n) {
    if (n.isParsed() || !n.isIndetermined()) {
      if (n.getUninomial() != null) {
        return n.getUninomial();

      } else if (n.isBinomial()) {
        StringBuilder sb = new StringBuilder();
        sb.append(n.getGenus());
        sb.append(" ");
        sb.append(n.getSpecificEpithet());
        if (n.getInfraspecificEpithet() != null) {
          sb.append(" ");
          sb.append(n.getInfraspecificEpithet());
        }
        if (n.getCultivarEpithet() != null) {
          sb.append(" ");
          sb.append(n.getCultivarEpithet());
        }
        return sb.toString();
      }
      // with or without genus - but not a binomial
      if (n.getInfragenericEpithet() != null) {
        return n.getInfragenericEpithet();
      }
    }
    return n.getScientificName();
  }

  public static String inItalics(String x) {
    return "<i>" + x + "</i>";
  }

  /**
   * Wraps a name in italics, but keeps any hybrid marker (×) and its surrounding spaces outside the italic tags.
   * E.g. "Ophrys × varvarae" becomes "&lt;i&gt;Ophrys&lt;/i&gt; × &lt;i&gt;varvarae&lt;/i&gt;".
   */
  private static String inItalicsKeepHybrid(String x) {
    if (x.indexOf(HYBRID_MARKER) < 0) {
      return inItalics(x);
    }
    // split around the hybrid marker (ignoring surrounding whitespace) and italicize the name parts only
    String[] parts = x.split("\\s*" + HYBRID_MARKER + "\\s*", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        if (sb.length() > 0) {
          sb.append(" ");
        }
        sb.append(HYBRID_MARKER).append(" ");
      }
      if (!parts[i].isEmpty()) {
        sb.append(inItalics(parts[i]));
      }
    }
    return sb.toString();
  }

  /**
   * build a scientific name without authorship from a parsed Name instance.
   */
  private static StringBuilder buildScientificName(FormattableName n) {
    StringBuilder sb = new StringBuilder();

    if (n.isCandidatus()) {
      sb.append("\"");
      sb.append("Candidatus ");
    }

    if (n.getUninomial() != null) {
      // higher rank names being just a uninomial!
      if (n.getNotho().contains(NamePart.GENERIC)) {
        sb.append(HYBRID_MARKER)
          .append(" ");
      }
      sb.append(n.getUninomial());

    } else {
      // bi- or trinomials or infrageneric names
      if (n.getInfragenericEpithet() != null) {
        if ((isUnknown(n.getRank()) && n.getSpecificEpithet() == null) || (n.getRank() != null && n.getRank().isInfragenericStrictly())) {
          boolean showInfraGen = true;
          // the infrageneric is the terminal rank. Always show it and wrap it with its genus if requested
          if (n.getGenus() != null) {
            appendGenus(sb, n);
            sb.append(" ");
            // we show infragenerics in brackets, unless its a botanical name
            // but use rank markers for botanical names (unless its no defined rank)
            if (NomCode.BOTANICAL != n.getCode()) {
              sb.append("(");
              if (n.getNotho().contains(NamePart.INFRAGENERIC)) {
                sb.append(HYBRID_MARKER)
                  .append(' ');
              }
              sb.append(n.getInfragenericEpithet());
              sb.append(")");
              showInfraGen = false;
            }
          }
          if (showInfraGen) {
            // For botanical names we use explicit rank markers, see http://www.iapt-taxon.org/nomen/main.php?page=art21
            if (NomCode.BOTANICAL == n.getCode()) {
              if (appendRankMarker(sb, n.getRank(), n.getNotho().contains(NamePart.INFRAGENERIC))) {
                sb.append(' ');
              }
            }
            sb.append(n.getInfragenericEpithet());
          }

        } else {
          if (n.getGenus() != null) {
            appendGenus(sb, n);
          }
          // additional subgenus shown for binomial. Always shown in brackets
          sb.append(" (");
          sb.append(n.getInfragenericEpithet());
          sb.append(")");
        }

      } else if (n.getGenus() != null) {
        appendGenus(sb, n);
      }

      if (n.getSpecificEpithet() == null) {
        if (n.getGenus() != null && n.getCultivarEpithet() == null) {
          if (n.getRank() != null && n.getRank().isSpeciesOrBelow()) {
            // no species epithet given, indetermined!
            if (n.getRank().isInfraspecific()) {
              // maybe we have an infraspecific epithet? force to show the rank marker
              appendInfraspecific(sb, n, true);
            } else if (!unparsedLeadsWithSpeciesMarker(n)) {
              // skip the synthetic "sp." when the unparsed portion already spells out a species
              // marker ("Xyris species 1"), so it isn't doubled into "Xyris sp. species 1"
              sb.append(" ");
              sb.append(n.getRank().getMarker());
            }
          }
        } else if (n.getInfraspecificEpithet() != null) {
          appendInfraspecific(sb, n, false);
        }

      } else {
        // species part
        sb.append(' ');
        if (n.getNotho().contains(NamePart.SPECIFIC)) {
          sb.append(HYBRID_MARKER)
            .append(" ");
        }
        sb.append(n.getSpecificEpithet());

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
          appendInfraspecific(sb, n, false);
        }
      }
    }

    // closing quotes for Candidatus names
    if (n.isCandidatus()) {
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

    // unparsed
    if (n.getUnparsed() != null) {
      sb.append(" ")
        .append(n.getUnparsed());
    }

    // add nom status
    //if (n.getNomenclaturalNote() != null) {
    //  appendIfNotEmpty(sb, ", ")
    //    .append(n.getNomenclaturalNote());
    //}
    return sb;
  }

  /** True when the unparsed portion already begins with a species marker (species/sp/spec), so the
   *  synthetic indet "sp." rank marker would only duplicate it. */
  private static boolean unparsedLeadsWithSpeciesMarker(FormattableName n) {
    return n.getUnparsed() != null && UNPARSED_SPECIES_MARKER.matcher(n.getUnparsed().trim()).find();
  }

  private static StringBuilder appendInfraspecific(StringBuilder sb, FormattableName n, boolean forceRankMarker) {
    // infraspecific part
    sb.append(' ');
    if (n.getNotho().contains(NamePart.INFRASPECIFIC)) {
      if (n.getRank() != null && isInfraspecificMarker(n.getRank())) {
        sb.append("notho");
      } else {
        sb.append(HYBRID_MARKER);
        sb.append(" ");
      }
    }
    // hide subsp. from zoological names
    if (forceRankMarker || isNotZoo(n.getCode()) || Rank.SUBSPECIES != n.getRank() || !n.getNotho().isEmpty()) {
      if (appendRankMarker(sb, n.getRank(), NameFormatter::isInfraspecificMarker, false) && n.getInfraspecificEpithet() != null) {
        sb.append(' ');
      }
    }
    if (n.getInfraspecificEpithet() != null) {
      sb.append(n.getInfraspecificEpithet());
    }
    return sb;
  }

  // The subsp./var./f. rank marker is the botanical convention; only an explicitly zoological
  // name drops it (zoological trinomials use bare epithets). A null/unknown code keeps the marker.
  private static boolean isNotZoo(NomCode code) {
    return code != NomCode.ZOOLOGICAL;
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

  private static StringBuilder appendGenus(StringBuilder sb, FormattableName n) {
    if (n.getNotho().contains(NamePart.GENERIC)) {
      sb.append(HYBRID_MARKER)
        .append(" ");
    }
    sb.append(n.getGenus());
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
   * Adds italics around the epithets but not rank markers or higher ranked names.
   */
  public static String scientificNameHtml(String scientificName, Rank rank){
    // only genus names and below are shown in italics
    if (scientificName != null && rank != null && rank.ordinal() >= Rank.GENUS.ordinal()) {
      Matcher m = RANK_MATCHER.matcher(scientificName);
      if (m.find()) {
        StringBuilder sb = new StringBuilder();
        sb.append(inItalicsKeepHybrid(m.group(1)));
        sb.append(" ");
        sb.append(m.group(2));
        if (m.group(3) != null) {
          sb.append(" ");
          sb.append(inItalicsKeepHybrid(m.group(3).trim()));
        }
        if (m.group(4) != null) {
          sb.append(" ");
          sb.append(m.group(4).trim());
        }
        return sb.toString();

      } else {
        m = LINNEAN_NAME_NO_AUTHOR.matcher(scientificName);
        if (m.find()) {
          return inItalicsKeepHybrid(scientificName);
        }
      }
    }
    //TODO: Candidatus or Ca.
    return scientificName;
  }

}
