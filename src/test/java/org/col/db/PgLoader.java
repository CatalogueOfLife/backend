package org.col.db;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.apache.commons.io.FilenameUtils;
import org.col.config.PgConfig;
import org.gbif.utils.file.FileUtils;
import org.gbif.utils.file.ResourcesUtil;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Utility that loads pg_dump generated tables dumps into the database using the native postgres jdbc copy command.
 * Expects for each table a tab separated CSV file with the suffix ".tsv". Use \N for null values.
 * <p>
 * It uses a single transaction so foreign key contrainst are only evaluated at the very end.
 * Therefore be careful with very large datasets.
 * <p>
 * This is a simple alternative to the full dbSetup framework which cannot deal with postgres enumerations with recent postgres jdbc drivers:
 * http://www.postgresql.org/message-id/CADK3HHJNjRxzqdOgo3w8S9ZcZ8TSGORvawBTYt3OUa_OneHz5A@mail.gmail.com
 */
public class PgLoader {
    private static final Logger LOG = LoggerFactory.getLogger(PgLoader.class);

    private static final String FILE_SUFFIX = ".tsv";
    private static final Joiner HEADER_JOINER = Joiner.on(",");

    public static Connection connect(PgConfig cfg) {
        try {
            String url = String.format("jdbc:postgresql://%s/%s", cfg.host, cfg.database);
            return DriverManager.getConnection(url, cfg.user, cfg.password);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param con    open postgres! connection
     * @param folder the classpath folder to scan for table data files
     * @throws Exception
     */
    public static void load(Connection con, String folder) throws Exception {
        con.setAutoCommit(false);
        LOG.info("Load data from >" + folder + "<");
        CopyManager copy = ((PGConnection) con).getCopyAPI();
        List<String> tables = listTables(folder);

        truncate(con, tables, false);
        con.commit();

        for (String table : tables) {
            LOG.debug("Load table >" + table + "<");
            InputStreamWithoutHeader in = new InputStreamWithoutHeader(FileUtils.classpathStream(folder + "/" + table + FILE_SUFFIX), '\t', '\n');
            String header = HEADER_JOINER.join(in.header);
            copy.copyIn("COPY " + table + "(" + header + ") FROM STDOUT WITH NULL '\\N'", in);
            updateSequence(con, table);
        }
        con.commit();
    }

    private static List<String> listTables(String folder) throws Exception {
        List<String> tables = Lists.newArrayList();
        for (String res : ResourcesUtil.list(PgLoader.class, folder)) {
            tables.add(FilenameUtils.removeExtension(res));
        }
        return tables;
    }

    public static void truncate(Connection con, String folder, boolean resetSequences) throws Exception {
        truncate(con, listTables(folder), resetSequences);
    }

    private static void truncate(Connection con, List<String> tables, boolean resetSequences) throws Exception {
        LOG.debug("Truncate tables");
        for (String table : tables) {
            if (!Strings.isNullOrEmpty(table)) {
                try (Statement st = con.createStatement()) {
                    st.execute("TRUNCATE " + table + " CASCADE");
                }
                if (resetSequences) {
                    updateSequence(con, table, 1);
                }
            }
        }
    }

    /**
     * Update postgres sequence counter for a given table.
     */
    private static void updateSequence(Connection con, String table) {
        updateSequence(con, table, null);
    }

    private static void updateSequence(Connection con, String table, Integer start) {
        try {
            try (Statement st = con.createStatement()) {
                StringBuilder sb = new StringBuilder();
                sb.append("SELECT pg_catalog.setval(pg_get_serial_sequence('");
                sb.append(table);
                sb.append("', 'key'), ");
                if (start != null) {
                    sb.append(start);
                    sb.append(")");
                } else {
                    sb.append(" MAX(key)) FROM ");
                    sb.append(table);
                }

                st.execute(sb.toString());
                con.commit();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static class InputStreamWithoutHeader extends InputStream {

        private final char delimiter;
        private final char lineend;
        private final InputStream stream;
        private final List<String> header = Lists.newArrayList();

        public InputStreamWithoutHeader(InputStream stream, char delimiter, char lineEnding) {
            this.delimiter = delimiter;
            this.lineend = lineEnding;
            this.stream = stream;
            readHeader();
        }

        private void readHeader() {
            try {
                int x = stream.read();
                StringBuffer sb = new StringBuffer();
                while (x >= 0) {
                    char c = (char) x;
                    if (c == delimiter) {
                        header.add(sb.toString());
                        sb = new StringBuffer();
                    } else if (c == lineend) {
                        header.add(sb.toString());
                        break;
                    } else {
                        sb.append(c);
                    }
                    x = stream.read();
                }
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }

        @Override
        public int available() throws IOException {
            return stream.available();
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }

        @Override
        public void mark(int readlimit) {
            stream.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return stream.markSupported();
        }

        @Override
        public int read() throws IOException {
            return stream.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return stream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return stream.read(b, off, len);
        }

        @Override
        public void reset() throws IOException {
            stream.reset();
        }

        @Override
        public long skip(long n) throws IOException {
            return stream.skip(n);
        }
    }

}
