package life.catalogue.junit;

import life.catalogue.api.model.ApiLog;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.reflect.ClassPath;

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
      connectionTest(c);
    }
  }

  public static void connectionTest(Connection c) throws Exception {
    try (Statement st = c.createStatement()) {
      st.execute("SHOW SERVER_ENCODING");
      st.getResultSet().next();
      String cs = st.getResultSet().getString(1);
      assertEquals("Bad server encoding", "UTF8", cs);

      st.execute("SHOW CLIENT_ENCODING");
      st.getResultSet().next();
      cs = st.getResultSet().getString(1);
      assertEquals("Bad client encoding", "UTF8", cs);
    }
  }

  /**
   * Tests the existance and completeness of pg enums against java enums.
   * To create new SQL use PrintPgEnumSql
   */
  @Test
  public void pgEnums() throws Exception {
    try (Connection c = pgSetupRule.connect()) {
      for (Class<? extends Enum<?>> cl : listEnums(false)) {
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
  
  public static List<Class<? extends Enum<?>>> listEnums(boolean coldpOnly) throws IOException {
    ClassPath cp = ClassPath.from(DatasetType.class.getClassLoader());
    List<Class<? extends Enum<?>>> enums = new ArrayList<>();
    addPackageEnums(cp, enums, DatasetType.class.getPackage());
    addPackageEnums(cp, enums, Rank.class.getPackage());
    if (!coldpOnly) {
      enums.add(Sector.Mode.class);
      enums.add(EditorialDecision.Mode.class);
      enums.add(User.Role.class);
      enums.add(ApiLog.HttpMethod.class);
    }
    // not needed for persistency
    enums.remove(BioGeoRealm.class);
    enums.remove(Country.class);
    enums.remove(TxtTreeTerm.class);
    enums.remove(Frequency.class);
    enums.remove(GeoTimeType.class);
    enums.remove(Setting.class);
    enums.remove(IgnoreReason.class);
    enums.remove(MatchingMode.class);
    enums.remove(MetadataFormat.class);
    enums.remove(NameCategory.class);
    enums.remove(NameField.class);
    enums.remove(DoiResolution.class);
    enums.remove(TabularFormat.class);
    enums.remove(TaxGroup.class);
    // remove enums not used in coldp
    if (coldpOnly) {
      enums.remove(DataFormat.class);
      enums.remove(DatasetOrigin.class);
      enums.remove(DatasetType.class);
      enums.remove(EntityType.class);
      enums.remove(ImportState.class);
      enums.remove(InfoGroup.class);
      enums.remove(Issue.class);
      enums.remove(JobStatus.class);
      enums.remove(License.class);
      enums.remove(MatchType.class);
      enums.remove(NameType.class);
      enums.remove(Origin.class);
    }
    // sort and print
    enums.sort(Comparator.comparing(cl -> BaseEnumSetTypeHandler.pgEnumName(cl)));
    return enums;
  }
  
  private static void addPackageEnums(ClassPath cp, List<Class<? extends Enum<?>>> enums, Package pack){
    for (ClassPath.ClassInfo cli : cp.getTopLevelClasses(pack.getName())) {
      Class<?> cl = cli.load();
      if (cl.isEnum()) {
        enums.add((Class<Enum<?>>) cl);
      }
    }
  }
  
}