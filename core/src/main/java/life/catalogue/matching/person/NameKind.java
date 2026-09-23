package life.catalogue.matching.person;

/**
 * How a name form cites a person.
 */
public enum NameKind {
  /** a botanical standard form, from IPNI or Wikidata P428 */
  STANDARD,
  /** a zoological author citation, from Wikidata P835 or ZooBank */
  CITATION,
  /** the full name of the person */
  FULL,
  /** any other spelling or alias */
  VARIANT
}
