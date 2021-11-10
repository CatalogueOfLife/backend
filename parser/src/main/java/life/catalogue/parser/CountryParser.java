package life.catalogue.parser;

import life.catalogue.api.vocab.Country;
import life.catalogue.common.text.CSVUtils;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * CoL country parser wrapping the GBIF country parser
 */
public class CountryParser extends EnumParser<Country> {
  // subregions can be of max 3 chars
  static final String ISO  = "([a-z]{2})(?:\\s*-\\s*([a-z0-9]{1,3}))?";
  private static final Pattern ISO_PATTERN = Pattern.compile("^"+ISO+"$", Pattern.CASE_INSENSITIVE);
  public static final CountryParser PARSER = new CountryParser();

  public CountryParser() {
    super("iso3166/country.csv", Country.class);
    //    0           ,    1           ,    2           ,    3           ,    4           ,    5           ,     6           ,     7           ,     8           ,              9                 ,              10             ,             11            ,         12          ,              13             , 14,          15        ,          16       ,          17         ,          18        ,          19         ,          20        ,          21        ,          22       ,          23         ,          24        ,          25         ,          26        ,        27       , 28    ,  29     ,30,Developed / Developing Countries,Dial,EDGAR,FIFA,FIPS,GAUL,Geoname ID,Global Code,Global Name,IOC,ITU,Intermediate Region Code,Intermediate Region Name,Land Locked Developing Countries (LLDC),Languages,Least Developed Countries (LDC),MARC,Region Code,Region Name,Small Island Developing States (SIDS),Sub-region Code,Sub-region Name,TLD,WMO,is_independent
    //official_name_ar,official_name_cn,official_name_en,official_name_es,official_name_fr,official_name_ru,ISO3166-1-Alpha-2,ISO3166-1-Alpha-3,ISO3166-1-numeric,ISO4217-currency_alphabetic_code,ISO4217-currency_country_name,ISO4217-currency_minor_unit,ISO4217-currency_name,ISO4217-currency_numeric_code,M49,UNTERM Arabic Formal,UNTERM Arabic Short,UNTERM Chinese Formal,UNTERM Chinese Short,UNTERM English Formal,UNTERM English Short,UNTERM French Formal,UNTERM French Short,UNTERM Russian Formal,UNTERM Russian Short,UNTERM Spanish Formal,UNTERM Spanish Short,CLDR display name,Capital,Continent,DS,Developed / Developing Countries,Dial,EDGAR,FIFA,FIPS,GAUL,Geoname ID,Global Code,Global Name,IOC,ITU,Intermediate Region Code,Intermediate Region Name,Land Locked Developing Countries (LLDC),Languages,Least Developed Countries (LDC),MARC,Region Code,Region Name,Small Island Developing States (SIDS),Sub-region Code,Sub-region Name,TLD,WMO,is_independent
    CSVUtils.parse(getClass().getResourceAsStream("/parser/dicts/iso3166/country-codes_csv.csv")).forEach(row -> {
      Optional<Country> opt = Country.fromIsoCode(row.get(7));
      opt.ifPresent(c -> {
        addNoOverwrite(row.get(0), c); // official_name_ar
        addNoOverwrite(row.get(1), c); // official_name_cn
        addNoOverwrite(row.get(2), c); // official_name_en
        addNoOverwrite(row.get(3), c); // official_name_es
        addNoOverwrite(row.get(4), c); // official_name_fr
        addNoOverwrite(row.get(5), c); // official_name_ru
        for (int idx = 14; idx<28; idx++) {
          addNoOverwrite(row.get(idx), c); // M49 & UNTERM & CDLR
        }
      });
    });
  
    // also make sure we have all official iso countries mapped
    for (Country c : Country.values()) {
      add(c.getName(), c);
      add(c.getIso2LetterCode(), c);
      add(c.getIso3LetterCode(), c);
      add(c.getIsoNumericalCode(), c);
    }
  }

  @Override
  String normalize(String x) {
    if (x != null) {
      var m = ISO_PATTERN.matcher(x);
      if (m.find()) {
        x = m.group(1);
      }
    }
    return super.normalize(x);
  }

}
