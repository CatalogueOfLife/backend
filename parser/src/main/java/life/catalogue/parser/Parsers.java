package life.catalogue.parser;

import java.util.*;

/**
 * Shared registry of the value parsers exposed by the API, keyed by a lower-case type name.
 * Used by ParserResource and the parser reconciliation resources so the exposed set stays in sync.
 * Holds only the vocabulary/scalar value parsers; the name parser lives in its own resource.
 */
public class Parsers {
  public static final Map<String, Parser<?>> VOCAB;
  static {
    Map<String, Parser<?>> m = new HashMap<>();
    m.put("boolean", BooleanParser.PARSER);
    m.put("country", CountryParser.PARSER);
    m.put("datasettype", DatasetTypeParser.PARSER);
    m.put("date", DateParser.PARSER);
    m.put("distributionstatus", DistributionStatusParser.PARSER);
    m.put("gazetteer", GazetteerParser.PARSER);
    m.put("geotime", GeoTimeParser.PARSER);
    m.put("integer", IntegerParser.PARSER);
    m.put("language", LanguageParser.PARSER);
    m.put("license", LicenseParser.PARSER);
    m.put("lifezone", EnvironmentParser.PARSER);
    m.put("mediatype", MediaTypeParser.PARSER);
    m.put("nomcode", NomCodeParser.PARSER);
    m.put("nomreltype", NomRelTypeParser.PARSER);
    m.put("nomstatus", NomStatusParser.PARSER);
    m.put("rank", RankParser.PARSER);
    m.put("referencetype", ReferenceTypeParser.PARSER);
    m.put("sex", SexParser.PARSER);
    m.put("taxonomicstatus", TaxonomicStatusParser.PARSER);
    m.put("treatmentformat", TreatmentFormatParser.PARSER);
    m.put("typestatus", TypeStatusParser.PARSER);
    m.put("uri", UriParser.PARSER);
    VOCAB = Collections.unmodifiableMap(m);
  }

  private Parsers() {}

  public static Parser<?> get(String type) {
    return type == null ? null : VOCAB.get(type.toLowerCase());
  }

  public static List<String> names() {
    List<String> names = new ArrayList<>(VOCAB.keySet());
    Collections.sort(names);
    return names;
  }
}
