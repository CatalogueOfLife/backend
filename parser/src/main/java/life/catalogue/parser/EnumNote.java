package life.catalogue.parser;

import java.util.Objects;

/**
 * Wrapper for an enumeration value accompanied by a note string field
 * to expose custom parsed details.
 */
public class EnumNote<T extends Enum> {
  public final T val;
  public final String note;
  
  public EnumNote(T val, String note) {
    this.val = val;
    this.note = note;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EnumNote<?> enumNote = (EnumNote<?>) o;
    return Objects.equals(val, enumNote.val) &&
        Objects.equals(note, enumNote.note);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(val, note);
  }
}
