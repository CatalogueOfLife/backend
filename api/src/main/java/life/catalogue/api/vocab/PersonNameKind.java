package life.catalogue.api.vocab;



/**
 * How a name form cites a person.
 */
public enum PersonNameKind {
  /** a botanical standard form, from IPNI or Wikidata P428 */
  STANDARD,
  /** a zoological author citation, from Wikidata P835 or ZooBank */
  CITATION,
  /** the full name of the person */
  FULL,
  /** any other spelling or alias */
  VARIANT,
  /** derived from the structured name and full names of the person when the registry is written or loaded: initials, family name and suffix */
  DERIVED
}
