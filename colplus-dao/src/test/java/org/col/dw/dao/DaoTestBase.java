package org.col.dw.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.dw.db.mapper.InitMybatisRule;
import org.col.dw.db.mapper.PgSetupRule;
import org.junit.ClassRule;
import org.junit.Rule;

public abstract class DaoTestBase {

	@ClassRule
	public static PgSetupRule pgSetupRule = new PgSetupRule();

	@Rule
	public InitMybatisRule initMybatisRule = InitMybatisRule.squirrels();

	protected SqlSession session() {
		return initMybatisRule.getSqlSession();
	}

	public <T> T mapper(Class<T> mapperClazz) {
		return session().getMapper(mapperClazz);
	}
}
