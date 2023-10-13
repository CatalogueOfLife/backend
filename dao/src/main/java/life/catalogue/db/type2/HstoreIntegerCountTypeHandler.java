package life.catalogue.db.type2;

public class HstoreIntegerCountTypeHandler extends HstoreCountTypeHandlerBase<Integer> {
  
  @Override
  Integer toKey(String x) throws IllegalArgumentException {
    return Integer.parseInt(x);
  }
}
