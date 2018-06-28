package org.col.db.mapper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.col.db.PgConfig;
import org.junit.rules.ExternalResource;

/**
 * A junit test rule that truncates all CoL tables, potentially loads some test
 * data from a sql dump file. Do not modify the db schema in the sql files.
 *
 * The rule was designed to run as a junit {@link org.junit.Rule} before every
 * test.
 *
 * This rule requires a running postgres server via the {@link PgSetupRule}.
 * Make sure its setup!
 */
public class InitMybatisRule extends ExternalResource {

	final private TestData testData;
	private SqlSession session;

	public enum TestData {
		NONE,
    APPLE,

		/**
		 * Inits the datasets table with real col data from colplusthe -repo
		 */
		DATASETS
	}

	public static InitMybatisRule empty() {
		return new InitMybatisRule(TestData.NONE);
	}

	public static InitMybatisRule apple() {
		return new InitMybatisRule(TestData.APPLE);
	}

	public static InitMybatisRule datasets() {
		return new InitMybatisRule(TestData.DATASETS);
	}

	private InitMybatisRule(TestData testData) {
		this.testData = testData;
	}

	public <T> T getMapper(Class<T> mapperClazz) {
		return session.getMapper(mapperClazz);
	}

	public void commit() {
		session.commit();
	}

	public SqlSession getSqlSession() {
		return session;
	}

	@Override
	protected void before() throws Throwable {
		super.before();
		truncate();
		loadData();
		System.out.println("Open new mybatis session");
		session = PgSetupRule.getSqlSessionFactory().openSession(false);
	}

	@Override
	protected void after() {
		super.after();
		session.close();
	}

	private void truncate() {
		System.out.println("Truncate tables");
		try (Connection con = PgSetupRule.getConnection()) {
			con.setAutoCommit(false);
			java.sql.Statement st = con.createStatement();
			st.execute("TRUNCATE dataset CASCADE");
			con.commit();
			st.close();

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private void loadData() {
		if (testData != TestData.NONE) {
			System.out.format("Load %s test data\n\n", testData);
			try (Connection con = PgSetupRule.getConnection()) {
				con.setAutoCommit(false);
				ScriptRunner runner = new ScriptRunner(con);
				runner.setSendFullScript(true);

				switch (testData) {
					case DATASETS:
						// common data
						runner.runScript(Resources.getResourceAsReader(PgConfig.DATA_FILE));
						con.commit();
						// COL GSDs
						try (Reader datasets = new InputStreamReader(PgConfig.COL_DATASETS_URI.toURL().openStream(), StandardCharsets.UTF_8)) {
							runner.runScript(datasets);
							con.commit();
						}
						// GBIF Backbone datasets
						runner.runScript(Resources.getResourceAsReader(PgConfig.GBIF_DATASETS_FILE));
						break;
					default:
						runner.runScript(Resources.getResourceAsReader(testData.name().toLowerCase() + ".sql"));
				}
				con.commit();

			} catch (SQLException | IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
