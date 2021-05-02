package life.catalogue.es.query;

/**
 * Represents the value you are matching your documents against (using tokenization and scoring as in full-text
 * queries).
 */
@SuppressWarnings("unused")
class MatchConstraint extends Constraint {

  private final String query; // The search phrase
  
  private AbstractMatchQuery.Operator operator;

  MatchConstraint(String query) {
    this.query = query;
  }

  void operator(AbstractMatchQuery.Operator operator) {
    this.operator = operator;
  }

}
