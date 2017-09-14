package org.col.db;

import org.col.config.ConfigTestUtils;
import org.junit.Test;

/**
 * Simply checks no exceptions occurr when loading the standard squirrelts dataset.
 */
public class PgLoaderTest {

    @Test
    public void testLoad() throws Exception {
        PgLoader.load(PgLoader.connect(ConfigTestUtils.testConfig()), "quatsch");
    }
}