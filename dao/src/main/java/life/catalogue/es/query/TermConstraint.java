package life.catalogue.es.query;

@SuppressWarnings("unused")
class TermConstraint extends Constraint {

  private final Object value;

  TermConstraint(Object value) {
    this.value = value;
  }

}
