package life.catalogue.matching.person;

/**
 * How two persons are related. Relatives working in one field are what author strings cannot tell apart.
 */
public enum RelationType {
  /** the other person is a parent of the person */
  PARENT,
  SIBLING
}
