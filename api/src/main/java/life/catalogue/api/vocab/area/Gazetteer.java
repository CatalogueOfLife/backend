package life.catalogue.api.vocab.area;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.URI;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 *
 */
public enum Gazetteer {

  TDWG("World Geographical Scheme for Recording Plant Distributions",
    "http://www.tdwg.org/standards/109",
    null,
    "World Geographical Scheme for Recording Plant Distributions published by TDWG at level 1, 2, 3 or 4. " +
      " Level 1 = Continents," +
      " Level 2 = Regions," +
      " Level 3 = Botanical countries," +
      " Level 4 = Basic recording units.",
      false,
      "^(?:[1-9]|[1-9][0-9]|[A-Z]{3}|[A-Z]{3}-[A-Z]{2})$",
      String::toUpperCase,
      GenericArea.class
  ),

  /**
   * Mostly synonymous are the <a href="http://www.fao.org/countryprofiles/iso3list/en/">FAO ISO 3 letter country codes</a>.
   *
   * @see <a href="https://en.wikipedia.org/wiki/ISO_3166">ISO 3166</a>
   * @see <a href="https://en.wikipedia.org/wiki/ISO_3166-1">ISO 3166-1</a>
   * @see <a href="https://en.wikipedia.org/wiki/ISO_3166-2">ISO 3166-2</a>
   * @see <a href="https://en.wikipedia.org/wiki/ISO_3166-3">ISO 3166-3</a>
   * @see <a href="https://www.iso.org/obp/ui/">ISO Code Browser</a>
   */
  ISO("ISO 3166 Country Codes",
    "https://en.wikipedia.org/wiki/ISO_3166",
    "https://www.iso.org/obp/ui/#iso:code:3166:",
    "ISO 3166 codes for the representation of names of countries and their subdivisions. " +
      "Codes for current countries (ISO 3166-1), " +
      "country subdivisions (ISO 3166-2) " +
      "and formerly used names of countries (ISO 3166-3). " +
      "Country codes should be given as alpha-2 codes.",
      false,
      "^[A-Z]{2}(-[A-Z0-9]{1,6})?$",
      String::toUpperCase,
      Country.class
  ),

  FAO("FAO Major Fishing Areas",
    "http://www.fao.org/fishery/cwp/handbook/H/en",
    "https://www.fao.org/fishery/en/area/",
    "FAO Major Fishing Areas",
      true,
      "^[0-9]{2}(?:\\.(?:[0-9]+|[A-Z])(?:\\.(?:[0-9]+|[A-Za-z])){0,3})?$",
      String::toLowerCase,
    GenericArea.class
  ),

  LONGHURST("Longhurst Biogeographical Provinces",
    "http://www.marineregions.org/sources.php#longhurst",
    null,
    "Longhurst Biogeographical Provinces, a partition of the world oceans into provinces as defined by Longhurst, A.R. (2006). " +
      "Ecological Geography of the Sea. 2nd Edition.",
      false,
      "^[A-Z]{4}$",
      String::toUpperCase,
      GenericArea.class
  ),

  REALM("Biogeographic Realms",
    "https://en.wikipedia.org/wiki/Biogeographic_realm",
    null,
    "Enumeration of 8 traditional terrestrial biogeographic realms dividing the Earth's land surface based on distributional patterns of terrestrial organisms.",
      false,
      "^[A-Z][a-z]+$",
      String::toLowerCase,
      GenericArea.class
  ),

  IHO("International Hydrographic Organization See Areas",
    "https://github.com/CatalogueOfLife/col-gazetteers/iho/S23_1953.pdf",
    null,
    "S-23 integer numbers for sea areas published by the International Hydrographic Organization as boundaries of the major oceans and seas of the world. " +
      "See Limits of Oceans & Seas, Special Publication No. 23 published by the International Hydrographic Organization in 1953.",
      true,
      "^[0-9]+[a-zA-Z]?$",
      null,
      GenericArea.class
  ),

  MRGID("Marine Regions Geographic Identifier",
    "https://www.marineregions.org/gazetteer.php",
    "http://marineregions.org/mrgid/",
    "Standard, relational list of geographic names developed by VLIZ covering mainly marine names such as seas, sandbanks, ridges, bays or even standard sampling stations used in marine research." +
      "The geographic cover is global; however the gazetteer is focused on the Belgian Continental Shelf, the Scheldt Estuary and the Southern Bight of the North Sea.",
      false,
      "^[0-9]+$",
      null,
      GenericArea.class
  ),

  TEOW("Terrestrial Ecoregions of the World",
      null,
      null,
      "Terrestrial Ecoregions of the World. Dinerstein et al. 2017, update of Olson 2001 WWF TEOW",
      false,
      "^[0-9]+$",
      null,
      GenericArea.class
  ),

  TEXT("Free Text",
    null,
    null,
    "Free text not following any standard",
      false,
      ".+",
      null,
      GenericArea.class
  );


  Gazetteer(String title, String link, String areaLink, String description, boolean caseSensitive, String pattern, Function<String, String> normalizer, Class<? extends Area> areaCLass) {
    this.title = title;
    this.link = link == null ? null : URI.create(link);
    this.areaLinkTemplate = areaLink;
    this.description = description;
    this.caseSensitive = caseSensitive;
    this.pattern = pattern;
    this.normalizer = normalizer == null ? String::trim : normalizer;
    this.regex = Pattern.compile(pattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
    this.areaCLass = areaCLass;
  }

  private final String title;
  private final URI link;
  private final String description;
  private final String areaLinkTemplate;
  private final String pattern;
  private final Pattern regex;
  private final boolean caseSensitive;
  private final Function<String, String> normalizer;
  private final Class<? extends Area> areaCLass;

  public String getTitle() {
    return title;
  }

  public URI getLink() {
    return link;
  }

  public URI getAreaLink(String id) {
    if (areaLinkTemplate != null) {
      if (this == FAO && id.contains(".")) {
        // FAO only provides pages for the main division
        id = id.split("\\.", 2)[0];
      }
      return URI.create(areaLinkTemplate + id);

    } else if (this != TEXT) {
      // default to API URLs for all non text gazetteers that do not provide a custom template
      return URI.create("https://api.checklistbank.org/vocab/area/" + locationID(id));
    }
    return null;
  }

  public String getDescription() {
    return description;
  }

  /**
   * @return a prefix for the standard suitable to build a joined locationID of the form STANDARD:AREA
   */
  public String prefix() {
    return name().toLowerCase();
  }

  public String getPattern() {
    return pattern;
  }

  @JsonIgnore
  public Pattern getRegex() {
    return regex;
  }

  public boolean isCaseSensitive() {
    return caseSensitive;
  }

  public Class<? extends Area> getAreaCLass() {
    return areaCLass;
  }

  @JsonIgnore
  public Function<String, String> getNormalizer() {
    return normalizer;
  }

  public String normalize(String id) {
    return id == null ? null : (normalizer == null ? id : normalizer.apply(id.trim()));
  }

  /**
   * @return a locationID of the form STANDARD:AREA
   */
  public String locationID(String area) {
    return prefix() + ":" + area;
  }

  public static Gazetteer of(String prefix) throws IllegalArgumentException {
    return Gazetteer.valueOf(prefix.toUpperCase());
  }
}
