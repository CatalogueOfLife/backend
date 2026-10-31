package life.catalogue.api.vocab;


import life.catalogue.api.util.VocabularyUtils;

/**
 * Enumeration for all continents based on the 7 number model found on
 * <a href="http://en.wikipedia.org/wiki/Continent#Number_of_continents">Wikipedia</a>.
 * <p>
 * In particular this splits the Americas into North and South America with North America including the Caribbean and
 * reaching down and including Panama. See the <a href="http://upload.wikimedia.org/wikipedia/commons/1/19/Continents_vide_couleurs.png">Wikipedia continent map</a> for the exact divisions.
 */
public enum Continent {
  
  AFRICA("Africa"),
  ANTARCTICA("Antarctica"),
  ASIA("Asia"),
  OCEANIA("Oceania"),
  EUROPE("Europe"),
  /**
   * North America includes the Caribbean and
   * reachies down and includes Panama.
   */
  NORTH_AMERICA("North America"),
  SOUTH_AMERICA("South America");
  
  private final String title;
  private final String areaID;

  Continent(String title) {
    this.title = title;
    this.areaID = "continent:" + title.replaceAll(" ", "");
  }
  
  /**
   * @return the continent name in the English language.
   */
  public String getTitle() {
    return title;
  }

  /**
   * @return the area ID for this continent as used in the continent gazetteer.
   */
  public String getAreaID() {
    return areaID;
  }

  /**
   * @param continent name
   * @return the matching continent or null
   */
  public static Continent fromString(String continent) {
    try {
      return (Continent) VocabularyUtils.lookupEnum(continent, Continent.class);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
