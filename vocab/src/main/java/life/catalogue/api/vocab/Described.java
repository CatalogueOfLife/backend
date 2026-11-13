package life.catalogue.api.vocab;

/**
 * A vocabulary whose values each carry a human readable description of what they mean.
 * The /vocab API serves it as the description of every value, and the ChecklistBank UI shows it as help.
 */
public interface Described {

  /**
   * @return what the value means, never blank
   */
  String getDescription();
}
