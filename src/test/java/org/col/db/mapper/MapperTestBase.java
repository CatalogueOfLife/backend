package org.col.db.mapper;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.config.ConfigTestUtils;
import org.col.db.DbTestRule;
import org.col.db.MybatisBundle;
import org.junit.AfterClass;
import org.junit.Rule;

/**
 *
 */
public class MapperTestBase<T> {

    private static HikariDataSource dataSource;
    private static SqlSession session;

    T mapper;

    @Rule
    public DbTestRule dbSetup = DbTestRule.empty();

    public MapperTestBase(Class<T> mapperClazz) {
        initMybatis();
        mapper = session.getMapper(mapperClazz);
    }


    public static void initMybatis() {
        dataSource = ConfigTestUtils.testConfig().pool();
        SqlSessionFactory factory = MybatisBundle.configure(dataSource, "test");
        session = factory.openSession();
    }

    @AfterClass
    public static void tearDown() {
        session.close();
        dataSource.close();
    }

    public void commit() {
        session.commit();
    }

}