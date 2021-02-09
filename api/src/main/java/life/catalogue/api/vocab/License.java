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
  CC_BY("Creative Commons Attribution (CC-BY) 4.0", "http://creativecommons.org/licenses/by/4.0/legalcode"),

  /**
   * Creative Commons Attribution-NonCommercial version 4.0.
   *
   * @see <a href="http://creativecommons.org/licenses/by-nc/4.0/legalcode">legal document</a>
   */
  CC_BY_NC("Creative Commons Attribution Non Commercial (CC-BY-NC) 4.0", "http://creativecommons.org/licenses/by-nc/4.0/legalcode"),
  
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
  
  public final String title;
  public final String url;
  
  
  /**
   * Indicates if a license is a concrete license (true) or an abstracted license (false) like
   * UNSPECIFIED.
   *
   * @return the license if concrete or not
   */
  public boolean isConcrete() {
    return url != null;
  }

  /**
   * Check if source data under a given license can be used in a target project.
   * @param source license of the source
   * @param target license of the project
   * @return true if the source license is compatible with the target license
   */
  public static boolean isCompatible(License source, License target) {
    if (source == null || target == null || source==UNSPECIFIED || target==UNSPECIFIED || (source==target && source != OTHER)) {
      return true;
    }
    switch (source) {
      case CC0: return true;
      case CC_BY: return target==CC_BY_NC;
    }
    return false;
  }
}