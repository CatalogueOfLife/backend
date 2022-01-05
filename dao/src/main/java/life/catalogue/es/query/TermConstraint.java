package life.catalogue.es.query;

@SuppressWarnings("unused")
class TermConstraint extends Constraint {

  private final Object value;

  TermConstraint(Object value) {
    if (value.getClass().isEnum()) {
      this.value = ((Enum) value).ordinal();
    } else {
      this.value = value;
    }
  }

}
