package org.col.db.mapper;

import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A junit test rule that truncates all CoL tables, potentially loads some test data
 * from a sql dump file. Do not modify the db schema in the sql files.
 *
 * The rule was designed to run as a junit {@link org.junit.Rule} before every test.
 *
 * This rule requires a running postgres server via the {@link PgMybatisRule}.
 * Make sure its setup!
 */
public class DbInitRule implements TestRule {
  final private TestData testData;

  public enum TestData {NONE, SQUIRRELS}

  public static DbInitRule empty() {
    return new DbInitRule(TestData.NONE);
  }

  public static DbInitRule squirrels() {
    return new DbInitRule(TestData.SQUIRRELS);
  }

  private DbInitRule(TestData testData) {
    this.testData = testData;
  }


  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {

      @Override
      public void evaluate() throws Throwable {
        truncate();
        loadData();
        base.evaluate();
      }
    };
  }

  private void truncate() {
    System.out.println("Truncate tables");
    //TODO: implement tables truncation
  }

  private void loadData() {
    if (testData != TestData.NONE) {
      System.out.format("Load %s test data\n\n", testData);
      try (Connection con = PgMybatisRule.getConnection()) {
        ScriptRunner runner = new ScriptRunner(con);
        runner.runScript(Resources.getResourceAsReader(testData.name().toLowerCase()+".sql"));
        con.commit();

      } catch (SQLException | IOException e) {
        Throwables.propagate(e);
      }
    }
  }

}
