package life.catalogue.matching.person;

import life.catalogue.api.model.AuthorshipPersonMatch;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedAuthorship;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Standalone author matching: an author citation, or every author of a whole authorship, matched to the persons of the
 * registry and narrowed by the code, year and group of the name.
 */
public class PersonMatchService {

  /**
   * Splits an authorship into its authors: the name parser, but for tests.
   */
  @FunctionalInterface
  public interface AuthorshipParser {
    Optional<ParsedAuthorship> parse(String authorship, @Nullable NomCode code);
  }

  private final PersonResolver resolver;
  private final AuthorshipParser parser;

  public PersonMatchService(PersonStore store, PersonResolver.Margins margins) {
    this(store, margins, NameParser.PARSER::parseAuthorship);
  }

  public PersonMatchService(PersonStore store, PersonResolver.Margins margins, AuthorshipParser parser) {
    this.resolver = new PersonResolver(store, margins);
    this.parser = parser;
  }

  /**
   * @return RESOLVED with the one person the citation may name, AMBIGUOUS with several, UNKNOWN with none, or RULED_OUT
   *         with the persons it would name but for the year or group
   */
  public PersonMatch match(String citation, @Nullable NomCode code, @Nullable Integer year, @Nullable TaxGroup group) {
    String key = PersonKeys.key(citation);
    Set<Person> all = key == null ? Set.of() : resolver.candidates(citation, code);
    if (all.isEmpty()) {
      return new PersonMatch(citation, key, PersonMatch.Status.UNKNOWN, List.of());
    }
    Set<Person> possible = resolver.narrow(all, year, group);
    if (possible.isEmpty()) {
      return new PersonMatch(citation, key, PersonMatch.Status.RULED_OUT, List.copyOf(all));
    }
    var status = possible.size() == 1 ? PersonMatch.Status.RESOLVED : PersonMatch.Status.AMBIGUOUS;
    return new PersonMatch(citation, key, status, List.copyOf(possible));
  }

  /**
   * Splits an authorship with the name parser and matches every author: the combination and its ex authors by the year
   * of the combination, the basionym and its ex authors by the year of the basionym, the sanctioning author by none.
   */
  public AuthorshipPersonMatch matchAuthorship(String authorship, @Nullable NomCode code, @Nullable TaxGroup group) {
    Optional<ParsedAuthorship> parsed = parser.parse(authorship, code);
    if (parsed.isEmpty()) {
      return new AuthorshipPersonMatch(authorship, AuthorshipPersonMatch.Status.UNPARSABLE, List.of(), List.of(), List.of(),
        List.of(), List.of());
    }
    ParsedAuthorship pa = parsed.get();
    Authorship comb = pa.getCombinationAuthorship() == null ? new Authorship() : pa.getCombinationAuthorship();
    Authorship bas = pa.getBasionymAuthorship() == null ? new Authorship() : pa.getBasionymAuthorship();
    Integer combYear = PersonResolver.year(comb.getYear());
    Integer basYear = PersonResolver.year(bas.getYear());
    return new AuthorshipPersonMatch(authorship, AuthorshipPersonMatch.Status.PARSED,
      match(comb.getAuthors(), code, combYear, group),
      match(comb.getExAuthors(), code, combYear, group),
      match(bas.getAuthors(), code, basYear, group),
      match(bas.getExAuthors(), code, basYear, group),
      pa.getSanctioningAuthor() == null ? List.of() : List.of(match(pa.getSanctioningAuthor(), code, null, group)));
  }

  private List<PersonMatch> match(@Nullable List<String> authors, @Nullable NomCode code, @Nullable Integer year,
                                  @Nullable TaxGroup group) {
    return authors == null ? List.of() : authors.stream().map(a -> match(a, code, year, group)).toList();
  }
}
