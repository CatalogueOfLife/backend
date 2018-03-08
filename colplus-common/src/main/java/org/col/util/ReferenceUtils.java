package org.col.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.helper.json.JsonLexer;
import org.col.api.model.Reference;

import java.io.IOException;
import java.io.StringReader;

/**
 *
 */
public class ReferenceUtils {
  public static final String DEFAULT_STYLE = "ieee";
  private static final CSL citeproc;
  static {
    try {
      citeproc = new CSL(new ItemDataProvider() {
        @Override
        public CSLItemData retrieveItem(String id) {
          return null;
        }

        @Override
        public String[] getIds() {
          return new String[0];
        }
      }, "ieee");

    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String format(Reference ref) {
    try {
      return citeproc.makeAdhocBibliography(DEFAULT_STYLE, toCSL(ref)).makeString();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static CSLItemData toCSL(Reference ref) {
    ObjectMapper om = new ObjectMapper();
    String json = null;//om.writeValueAsString(ref.getCsl());
    JsonLexer lexer = new JsonLexer(new StringReader(json));
    return new CSLItemDataBuilder()
        .title(null)
        .build();
  }
}
