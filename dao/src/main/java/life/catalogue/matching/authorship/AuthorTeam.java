package life.catalogue.matching.authorship;

import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The authors of one team as {@link AuthorshipNormalizer#normalize(org.gbif.nameparser.api.Authorship,
 * org.gbif.nameparser.api.NomCode)} gives them, already selected by code, with the year of the authorship.
 *
 * @param year the year as given, not parsed: "1753", "184?", "1878 [1879]"
 */
public record AuthorTeam(List<String> authors, @Nullable String year) {
}
