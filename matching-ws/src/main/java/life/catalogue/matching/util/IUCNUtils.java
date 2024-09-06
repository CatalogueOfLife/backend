package life.catalogue.matching.util;

public class IUCNUtils {

  public static String formatIucn(String original){
    if (original == null) {
      return null;
    }
    // Trim the string
    String trimmed = original.trim();
    // Convert to uppercase
    String uppercased = trimmed.toUpperCase();
    // Replace any whitespace with a single underscore
    return uppercased.replaceAll("\\s+", "_");
  }

  public enum IUCN {
    EXTINCT("EX"),
    EXTINCT_IN_THE_WILD("EW"),
    CRITICALLY_ENDANGERED ("CR"),
    ENDANGERED ("EN"),
    VULNERABLE ("VU"),
    NEAR_THREATENED ("NT"),
    CONSERVATION_DEPENDENT ("CD"),
    LEAST_CONCERN ("LC"),
    DATA_DEFICIENT ("DD"),
    NOT_EVALUATED ("NE");

    private final String code;

    IUCN(String code) {
      this.code = code;
        }

    public String getCode() {
        return code;
    }

  }
}
