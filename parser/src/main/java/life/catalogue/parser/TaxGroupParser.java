package life.catalogue.parser;


import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses/interprets TaxGroup from various known taxon names that are unique and indicative for the resulting group.
 * Some names have been used in multiple groups and they will result in the lowest common denominator or nothing in case of cross kingdom names.
 * See PROBLEMS.MD for some cases.
 * This parser does not throw unparsable exceptions but instead returns an empty optional.
 */
public class TaxGroupParser extends EnumParser<TaxGroup> {
  private static final Logger LOG = LoggerFactory.getLogger(TaxGroupParser.class);
  public static final TaxGroupParser PARSER = new TaxGroupParser();

  public TaxGroupParser() {
    super("taxgroup.csv", false, TaxGroup.class);
    for (TaxGroup tg : TaxGroup.values()) {
      InputStream stream = getClass().getResourceAsStream("/parser/dicts/taxgroup/" + tg.name().toLowerCase() + ".txt");
      if (stream == null) {
        LOG.warn("No taxgroup file for {}", tg);
      } else {
        try (BufferedReader br = UTF8IoUtils.readerFromStream(stream)) {
          br.lines().forEach( name -> {
            if (!StringUtils.isBlank(name)) {
              var prev = add(name, tg);
              if (prev != null && prev != tg) {
                LOG.warn("Taxon name {} not unique for a single taxon group. Appears in both {} and {}", name, tg, prev);
                //throw new IllegalStateException(String.format("Taxon name %s not unique for a single taxon group. Appears in both %s and %s", name, tg, prev));
              }
            }
          });
        } catch (IOException e) {
          throw new RuntimeException("Error reading tax group " + tg, e);
        }
      }
    }
  }

}
