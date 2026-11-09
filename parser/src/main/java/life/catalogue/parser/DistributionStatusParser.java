package life.catalogue.parser;


import life.catalogue.api.vocab.DistributionStatus;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Parses DistributionStatus
 */
public class DistributionStatusParser extends EnumParser<DistributionStatus> {
  public static final DistributionStatusParser PARSER = new DistributionStatusParser();

  private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]+");
  /**
   * Status values declaring that a taxon does not occur in an area, as dwc:occurrenceStatus can do.
   * Extinct or extirpated is a former presence and doubtful an uncertain one, so neither is listed.
   */
  private static final Set<String> ABSENT = Set.of(
    "absent",
    "absence",
    "excluded",
    "not present",
    "notpresent",
    "non present",
    "not found",
    "not detected",
    "not recorded"
  );

  public DistributionStatusParser() {
    super("distributionstatus.csv", DistributionStatus.class);
  }

  /**
   * @return true if the status value declares the absence of a taxon from an area, e.g. an occurrenceStatus=absent
   */
  public static boolean isAbsent(@Nullable String status) {
    if (status == null) return false;
    String norm = SEPARATORS.matcher(status.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    return ABSENT.contains(norm);
  }

}
