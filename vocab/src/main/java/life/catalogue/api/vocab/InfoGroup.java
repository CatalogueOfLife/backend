package life.catalogue.api.vocab;

/**
 * Information group as a smaller unit of NameUsage information.
 */
public enum InfoGroup {
  AUTHORSHIP,
  /**
   * The publishedIn reference of a name, including a DOI or URL added to an existing reference.
   * Records written before PUBLISHED_IN_PAGE and PUBLISHED_IN_PAGE_LINK existed also use it for the page and its link.
   */
  PUBLISHED_IN,
  PARENT,
  BASIONYM,
  EXTINCT,
  TEMPORAL_RANGE,
  RANK,
  ETYMOLOGY,
  GENDER,
  ENVIRONMENT,
  /**
   * The page of the publishedIn reference given by a name.
   */
  PUBLISHED_IN_PAGE,
  /**
   * The link to the page of the publishedIn reference given by a name.
   */
  PUBLISHED_IN_PAGE_LINK;
}
