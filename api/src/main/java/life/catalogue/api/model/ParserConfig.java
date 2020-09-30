package life.catalogue.api.model;

import com.google.common.base.Strings;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserConfig extends Name {
  private static String[] EMPTY = new String[2];
  private static Pattern Splitter = Pattern.compile("^(.+)\\|(.*)$");
  private String taxonomicNote;
  private boolean extinct;

  private static String[] parseID(String id) {
    if (id != null) {
      Matcher m = Splitter.matcher(id);
      String[] parts = new String[2];
      if (m.find()) {
        parts[0] = m.group(1);
        parts[1] = m.group(2);
      } else {
        parts[0] = id;
        parts[1] = null;
      }
      return parts;
    }
    return EMPTY;
  }

  @Override
  public String getScientificName() {
    return parseID(getId())[0];
  }

  public void updateID(String scientificName, String authorship) {
    this.setId(Strings.nullToEmpty(scientificName).trim() + "|" + Strings.nullToEmpty(authorship).trim());
  }

  @Override
  public String getAuthorship() {
    return parseID(getId())[1];
  }

  public String getTaxonomicNote() {
    return taxonomicNote;
  }

  public void setTaxonomicNote(String taxonomicNote) {
    this.taxonomicNote = taxonomicNote;
  }

  public boolean getExtinct() {
    return extinct;
  }

  public void setExtinct(boolean extinct) {
    this.extinct = extinct;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ParserConfig that = (ParserConfig) o;
    return Objects.equals(taxonomicNote, that.taxonomicNote) &&
      Objects.equals(extinct, that.extinct);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), taxonomicNote, extinct);
  }
}
