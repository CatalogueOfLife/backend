package org.col.db.dao;

import org.col.db.mapper.InitMybatisRule;
import org.col.db.mapper.PgSetupRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests the dataset script init routine.
 * Warn: this requires online access to github hosted sql files!
 */
public class DatasetSqlTest {

	@ClassRule
	public static PgSetupRule pgSetupRule = new PgSetupRule();

	@Rule
	public InitMybatisRule initMybatisRule = InitMybatisRule.datasets();

	@Test
	public void nothing() throws Exception {
		System.out.println("Done");
	}
}

