package life.catalogue.parser;

import life.catalogue.api.vocab.NomStatus;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Wrapper for an enumeration value accompanied by a note string field
 * to expose custom parsed details.
 * <p>
 * A dictionary row can also declare the nomenclatural status its key implies. Sources regularly
 * squeeze a nomenclatural statement into their single taxonomic status column, e.g.
 * dwc:taxonomicStatus=nomen nudum, and this is where that second half of the statement is kept.
 * It is declared per row on purpose: looking the same value up in the nomenclatural dictionary
 * instead would misread the taxonomic verdicts that merely share its vocabulary - in zoology a
 * "valid" name is a statement about the taxon, not about the name.
 */
public class EnumNote<T extends Enum> {
  public final T val;
  public final String note;
  public final NomStatus nomStatus;

  public EnumNote(T val, String note) {
    this(val, note, null);
  }

  public EnumNote(T val, String note, @Nullable NomStatus nomStatus) {
    this.val = val;
    this.note = note;
    this.nomStatus = nomStatus;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EnumNote<?> enumNote = (EnumNote<?>) o;
    return Objects.equals(val, enumNote.val) &&
        Objects.equals(note, enumNote.note) &&
        nomStatus == enumNote.nomStatus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(val, note, nomStatus);
  }

  @Override
  public String toString() {
    return val + (note == null ? "" : " /" + note) + (nomStatus == null ? "" : " [" + nomStatus + "]");
  }
}
