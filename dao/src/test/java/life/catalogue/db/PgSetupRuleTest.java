package life.catalogue.db;

import com.google.common.base.Joiner;
import com.google.common.reflect.ClassPath;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.*;
import life.catalogue.db.type.BaseEnumSetTypeHandler;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class PgSetupRuleTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();


  /**
   * https://github.com/Sp2000/colplus-backend/issues/577
   */
  @Test
  public void connectionCharset() throws Exception {
    try (Connection c = pgSetupRule.connect()) {
      PgConfigTest.connectionTest(c);
    }
  }

  @Test
  public void pgEnums() throws Exception {
    try (Connection c = pgSetupRule.connect()) {
      for (Class<? extends Enum<?>> cl : listEnums()) {
        final String pgEnumName = BaseEnumSetTypeHandler.pgEnumName(cl);
        // make sure all ranks from our enum are present in postgres
        PreparedStatement pst = c.prepareStatement("SELECT ?::"+pgEnumName);
        for (Enum<?> e : cl.getEnumConstants()){
          pst.setString(1, e.name());
          pst.execute();
        }
        // ... and vice versa
        String expected = "{" + Joiner.on(",").join(cl.getEnumConstants()) + "}";
        Statement st = c.createStatement();
        st.execute("select enum_range(null::"+pgEnumName+")");
        st.getResultSet().next();
        Array arr = st.getResultSet().getArray(1);
        assertEquals(expected, arr.toString() );
      }
    }
  }
  
  private static List<Class<? extends Enum<?>>> listEnums() throws IOException {
    ClassPath cp = ClassPath.from(DatasetType.class.getClassLoader());
    List<Class<? extends Enum<?>>> enums = new ArrayList<>();
    addPackageEnums(cp, enums, DatasetType.class.getPackage());
    addPackageEnums(cp, enums, Rank.class.getPackage());
    enums.add(Sector.Mode.class);
    enums.add(EditorialDecision.Mode.class);
    enums.add(User.Role.class);
    // not needed for persistency
    enums.remove(Country.class);
    enums.remove(ColDwcTerm.class);
    enums.remove(CSLRefType.class);
    enums.remove(TxtTreeTerm.class);
    enums.remove(Frequency.class);
    enums.remove(GeoTimeType.class);
    enums.remove(Setting.class);
    enums.remove(IgnoreReason.class);
    // sort and print
    enums.sort(Comparator.comparing(cl -> BaseEnumSetTypeHandler.pgEnumName(cl)));
    return enums;
  }
  
  @Test
  public void pgEnumSql() throws Exception {
    for (Class<? extends Enum<?>> cle : listEnums()) {
      printType(cle);
    }
  }
  
  private static void addPackageEnums(ClassPath cp, List<Class<? extends Enum<?>>> enums, Package pack){
    for (ClassPath.ClassInfo cli : cp.getTopLevelClasses(pack.getName())) {
      Class<?> cl = cli.load();
      if (cl.isEnum()) {
        enums.add((Class<Enum<?>>) cl);
      }
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