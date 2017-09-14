package org.col.util;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class CleanupUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CleanupUtils.class);

    public static void registerCleanupHook(final File f) {
        Runtime.getRuntime().addShutdownHook(new Thread() {

            public void run() {
                if (f.exists()) {
                    LOG.debug("Deleting file {}", f.getAbsolutePath());
                    FileUtils.deleteQuietly(f);
                }
            }
        });
    }

}
