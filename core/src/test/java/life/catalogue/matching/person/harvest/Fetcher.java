package life.catalogue.matching.person.harvest;

/**
 * Fetches a URL. Sources take one so tests can hand in canned answers.
 */
@FunctionalInterface
public interface Fetcher {
  String get(String url) throws Exception;
}
