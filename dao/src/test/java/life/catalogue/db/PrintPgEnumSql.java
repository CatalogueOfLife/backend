package life.catalogue.db;

import life.catalogue.db.type.BaseEnumSetTypeHandler;

import org.junit.Test;

public class PrintPgEnumSql {
  
  @Test
  public void pgEnumSql() throws Exception {
    for (Class<? extends Enum<?>> cle : PgSetupRuleTest.listEnums()) {
      printType(cle);
    }
  }
  
  private <T extends Enum<?>> void printType(Class<T> clazz) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TYPE " + BaseEnumSetTypeHandler.pgEnumName(clazz) + " AS ENUM (\n");
    boolean first = true;
    for (T e : clazz.getEnumConstants()){
      if (first) {
        first = false;
      } else {
        sb.append(",\n");
      }
      sb.append("  '");
      sb.append(e.name());
      sb.append("'");
    }
    sb.append("\n);\n");
    System.out.println(sb);
  }
  
}