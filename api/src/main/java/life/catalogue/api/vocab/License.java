package life.catalogue.api.vocab;

/**
 * Enumeration of the set of licenses the Clearinghouse supports for applying to a dataset.
 * The license provides a standardised way to define appropriate uses of a dataset.
 * </br>
 * The recommended best practice is to use the most recent license version, which for CC-BY and CC-BY-NC is 4.0.
 * This is in line with the recommendation from Creative Commons.
 *
 * @see <a href="https://creativecommons.org/faq/#why-should-i-use-the-latest-version-of-the-creative-commons-licenses">Creative
 * Commons recommendation</a>
 */
public enum License {
  
  /**
   * Creative Commons Zero / Public Domain version 1.0. Technically a waiver, not a license.
   *
   * @see <a href="http://creativecommons.org/publicdomain/zero/1.0/legalcode">legal document</a>
   */
  CC0("Public Domain (CC0 1.0)", "http://creativecommons.org/publicdomain/zero/1.0/legalcode"),
  
  /**
   * Creative Commons Attribution version 4.0.
   *
   * @see <a href="http://creativecommons.org/licenses/by/4.0/legalcode">legal document</a>
   */
  CC_BY("Creative Commons Attribution (CC BY) 4.0", "http://creativecommons.org/licenses/by/4.0/legalcode"),

  CC_BY_SA("Creative Commons Attribution Share Alike (CC BY-SA) 4.0", "https://creativecommons.org/licenses/by-sa/4.0/legalcode"),

  /**
   * Creative Commons Attribution-NonCommercial version 4.0.
   *
   * @see <a href="http://creativecommons.org/licenses/by-nc/4.0/legalcode">legal document</a>
   */
  CC_BY_NC("Creative Commons Attribution Non Commercial (CC BY-NC) 4.0", "http://creativecommons.org/licenses/by-nc/4.0/legalcode"),

  CC_BY_ND("Creative Commons Attribution No Derivatives (CC BY-ND) 4.0", "https://creativecommons.org/licenses/by-nd/4.0/legalcode"),

  CC_BY_NC_SA("Creative Commons Attribution Non Commercial Share Alike (CC BY-NC-SA) 4.0", "https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode"),

  CC_BY_NC_ND("Creative Commons Attribution Non Commercial No Derivatives (CC BY-NC-ND) 4.0", "https://creativecommons.org/licenses/by-nc-nd/4.0/legalcode"),

  /**
   * No license has been specified.
   */
  UNSPECIFIED(null, null),
  
  /**
   * An unsupported license.
   */
  OTHER(null, null);
  
  License(String title, String url) {
    this.title = title;
    this.url = url;
  }
  
  private final String title;
  private final String url;

  public String getTitle() {
    return title;
  }

  public String getUrl() {
    return url;
  }

  /**
   * Indicates if a license is a creative commons license (true) or some other license (false) like
   * UNSPECIFIED.
   */
  public boolean isCreativeCommons() {
    return url != null;
  }

  public boolean isNoDerivatives() {
    return name().contains("ND");
  }

  public boolean isShareAlike() {
    return name().contains("SA");
  }

  public boolean isNonCommercial() {
    return name().contains("NC");
  }

  /**
   * Check if source data under a given license can be used in a target project.
   * Unspecified or null is taken as being compatible, while OTHERS is never.
   * @param source license of the source
   * @param target license of the project
   * @return true if the source license is compatible with the target license
   */
  public static boolean isCompatible(License source, License target) {
    if (source != null && source.isNoDerivatives()) {
      return false;
    }
    if (source == null || target == null
        || source==UNSPECIFIED || target==UNSPECIFIED
        || source==CC0
        || (source==target && source != OTHER)) {
      return true;
    }
    if (source != OTHER && target != OTHER) {
      // licenses are ordered by "restrictiveness":
      // https://wiki.creativecommons.org/wiki/Wiki/cc_license_compatibility
      return source.ordinal() <= target.ordinal();
    }
    return false;
  }
}