package org.col.db.mapper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.col.db.PgConfig;
import org.col.db.PgSetupRule;
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
		NONE(2),
    APPLE(2, 11, 12),

		/**
		 * Inits the datasets table with real col data from colplus-repo
		 */
		DATASETS(2);

		final Set<Integer> datasetKeys;

		TestData(Integer... datasetKeys) {
			if (datasetKeys == null) {
				this.datasetKeys = Collections.EMPTY_SET;
			} else {
				this.datasetKeys = ImmutableSet.copyOf(datasetKeys);
			}
		}
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
		System.out.println("Open new mybatis session");
		session = PgSetupRule.getSqlSessionFactory().openSession(false);
		partition();
		truncate();
		loadData();
	}

	@Override
	protected void after() {
		super.after();
		session.close();
	}

	private void partition() {
		final DatasetPartitionMapper pm = getMapper(DatasetPartitionMapper.class);
		for (Integer dk : testData.datasetKeys) {
			pm.delete(dk);
			pm.create(dk);
			pm.buildIndices(dk);
			session.commit();
		}
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
			try (Connection con = PgSetupRule.getConnection()) {
				con.setAutoCommit(false);
				ScriptRunner runner = new ScriptRunner(con);
				runner.setSendFullScript(true);

				// common data for all tests and even the empty one
				runner.runScript(Resources.getResourceAsReader(PgConfig.DATA_FILE));
				con.commit();

				if (testData != TestData.NONE) {
					System.out.format("Load %s test data\n\n", testData);
					switch (testData) {
						case DATASETS:
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
				}
				con.commit();

			} catch (SQLException | IOException e) {
				throw new RuntimeException(e);
			}
	}

}
