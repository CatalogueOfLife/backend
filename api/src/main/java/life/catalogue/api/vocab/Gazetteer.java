package life.catalogue.api.vocab;

import java.net.URI;

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
      " Level 4 = Basic recording units."),

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
      "Country codes can be given either as alpha-2, alpha-3 or numeric codes."),

  FAO("FAO Major Fishing Areas",
    "http://www.fao.org/fishery/cwp/handbook/H/en",
    "https://www.fao.org/fishery/en/area/",
    "FAO Major Fishing Areas"),

  LONGHURST("Longhurst Biogeographical Provinces",
    "http://www.marineregions.org/sources.php#longhurst",
    null,
    "Longhurst Biogeographical Provinces, a partition of the world oceans into provinces as defined by Longhurst, A.R. (2006). " +
      "Ecological Geography of the Sea. 2nd Edition."),

  REALM("Biogeographic Realms",
    "https://en.wikipedia.org/wiki/Biogeographic_realm",
    null,
    "Enumeration of 8 traditional terrestrial biogeographic realms dividing the Earth's land surface based on distributional patterns of terrestrial organisms."),

  IHO("International Hydrographic Organization See Areas",
    null,
    null,
    "Sea areas published by the International Hydrographic Organization as boundaries of the major oceans and seas of the world. " +
      "See Limits of Oceans & Seas, Special Publication No. 23 published by the International Hydrographic Organization in 1953."),

  MRGID("Marine Regions Geographic Identifier",
    "https://www.marineregions.org/gazetteer.php",
    "http://marineregions.org/mrgid/",
    "Standard, relational list of geographic names developed by VLIZ covering mainly marine names such as seas, sandbanks, ridges, bays or even standard sampling stations used in marine research." +
      "The geographic cover is global; however the gazetteer is focused on the Belgian Continental Shelf, the Scheldt Estuary and the Southern Bight of the North Sea."),

  TEXT("Free Text",
    null,
    null,
    "Free text not following any standard");


  Gazetteer(String title, String link, String areaLink, String description) {
    this.title = title;
    this.link = link == null ? null : URI.create(link);
    this.areaLinkTemplate = areaLink;
    this.description = description;
  }

  private final String title;
  private final URI link;
  private final String description;
  private final String areaLinkTemplate;

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
