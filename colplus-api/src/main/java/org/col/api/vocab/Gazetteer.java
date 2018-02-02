package org.col.api.vocab;

/**
 *
 */
public enum Gazetteer {

  /**
   * World Geographical Scheme for Recording Plant Distributions
   * published by TDWG at level 1, 2, 3 or 4.
   * Level 1 = Continents
   * Level 2 = Regions
   * Level 3 = Botanical countries
   * Level 4 = Basic recording units
   *
   * @see <a href="http://www.tdwg.org/standards/109">TDWG Standard</a>
   */
  TDWG,

  /**
   * ISO 3166-1 Country codes given either as alpha-2, alpha-3 or numeric codes.
   * Mostly synonymous are the <a href="http://www.fao.org/countryprofiles/iso3list/en/">FAO ISO 3 letter country codes</a>.
   *
   * @see <a href="https://www.iso.org/obp/ui/">ISO Code Browser</a>
   */
  ISO,

  /**
   * FAO Major Fishing Areas
   * @see <a href="http://www.fao.org/fishery/cwp/handbook/H/en">FAO Fishing Areas</a>
   */
  FAO,

  /**
   * Longhurst Biogeographical Provinces, a partition of the world oceans into provinces
   * as defined by Longhurst, A.R. (2006). Ecological Geography of the Sea. 2nd Edition.
   * @see <a href="http://www.marineregions.org/sources.php#longhurst">Longhurst provinces hosted at VLIZ</a>
   */
  LONGHURST,

  /**
   * Terrestrial Ecoregions of the World is a biogeographic regionalization of the Earth's terrestrial biodiversity.
   * See Olson et al. 2001. Terrestrial ecoregions of the world: a new map of life on Earth. Bioscience 51(11):933-938.
   * @see <a href="https://www.worldwildlife.org/publications/terrestrial-ecoregions-of-the-world">WWF</a>
   */
  TEOW,

  /**
   * Sea areas published by the International Hydrographic Organization as boundaries of the major oceans and seas of the world.
   * See Limits of Oceans & Seas, Special Publication No. 23 published by the International Hydrographic Organization in 1953.
   */
  IHO,

  /**
   * Free text not following any standard
   */
  TEXT;
}
