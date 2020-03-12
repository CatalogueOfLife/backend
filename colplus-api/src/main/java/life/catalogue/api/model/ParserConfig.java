package life.catalogue.api.model;

import com.google.common.base.Strings;

import java.util.Objects;

public class ParserConfig extends Name {
  private String taxonomicNote;

  private static String[] parseID(String id) {
    return id.split(id, 2);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ParserConfig that = (ParserConfig) o;
    return Objects.equals(taxonomicNote, that.taxonomicNote);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), taxonomicNote);
  }
}
