package net.ripe.db.whois.logsearch;

import com.google.common.io.Files;
import net.ripe.db.whois.api.httpserver.JettyBootstrap;
import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.internal.logsearch.LegacyLogFormatProcessor;
import net.ripe.db.whois.internal.logsearch.LogFileIndex;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import javax.sql.DataSource;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@Category(IntegrationTest.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(locations = {"classpath:applicationContext-internal-base.xml", "classpath:applicationContext-internal-test.xml"})
public class LogSearchLegacyFormatTestIntegration extends AbstractJUnit4SpringContextTests {
    @Autowired
    private JettyBootstrap jettyBootstrap;
    @Autowired
    private LegacyLogFormatProcessor legacyLogFormatProcessor;
    @Autowired
    private LogFileIndex logFileIndex;

    @Autowired
    private DataSource dataSource;

    private Client client;

    private static File indexDirectory = Files.createTempDir();
    private static File logDirectory = Files.createTempDir();
    private static final String API_KEY = "DB-WHOIS-logsearchtestapikey";

    @BeforeClass
    public static void setupClass() {
        System.setProperty("dir.logsearch.index", indexDirectory.getAbsolutePath());
        System.setProperty("dir.update.audit.log", logDirectory.getAbsolutePath());

        LogsearchTestHelper.setupDatabase(new JdbcTemplate(LogsearchTestHelper.createDataSource("")), "acl.database", "ACL", "acl_schema.sql");
    }

    @AfterClass
    public static void cleanupClass() {
        System.clearProperty("dir.logsearch.index");
        System.clearProperty("dir.update.audit.log");
    }

    @Before
    public void setup() {
        LogFileHelper.createLogDirectory(logDirectory);
        jettyBootstrap.start();
        client = ClientBuilder.newBuilder().build();
        LogsearchTestHelper.insertApiKey(API_KEY, dataSource);
    }

    @After
    public void cleanup() {
        LogFileHelper.deleteLogs(logDirectory);
        logFileIndex.removeAll();
        jettyBootstrap.stop(true);
    }

    @Test
    public void legacy_one_logfile() throws Exception {
        addToIndex(LogFileHelper.createBzippedLogFile(logDirectory, "20100101", "the quick brown fox"));

        final String response = getUpdates("quick");

        assertThat(response, containsString("Found 1 update log(s)"));
        assertThat(response, containsString("the quick brown fox"));
    }

    @Test
    public void legacy_one_logfile_no_duplicates() throws Exception {
        final File logfile = LogFileHelper.createBzippedLogFile(logDirectory, "20100101", "the quick brown fox");
        addToIndex(logfile);
        addToIndex(logfile);

        final String response = getUpdates("quick");

        assertThat(response, containsString("Found 1 update log(s)"));
        assertThat(response, containsString("the quick brown fox"));
    }

    @Test
    public void legacy_log_directory_one_logfile() throws Exception {
        LogFileHelper.createBzippedLogFile(logDirectory, "20100101", "the quick brown fox");
        addToIndex(logDirectory);

        final String response = getUpdates("quick");

        assertThat(response, containsString("Found 1 update log(s)"));
        assertThat(response, containsString("the quick brown fox"));
    }

    @Test
    public void legacy_log_directory_multiple_logfiles() throws Exception {
        LogFileHelper.createBzippedLogFile(logDirectory, "20100101", "the quick brown fox");
        LogFileHelper.createBzippedLogFile(logDirectory, "20100102", "the quick brown fox");
        addToIndex(logDirectory);

        final String response = getUpdates("quick");

        assertThat(response, containsString("Found 2 update log(s)"));
        assertThat(response, containsString("the quick brown fox"));
    }

    @Test
    public void legacy_log_directory_no_duplicates() throws Exception {
        LogFileHelper.createBzippedLogFile(logDirectory, "20100101", "the quick brown fox");
        addToIndex(logDirectory);
        addToIndex(logDirectory);

        final String response = getUpdates("quick");

        assertThat(response, containsString("Found 1 update log(s)"));
        assertThat(response, containsString("the quick brown fox"));
    }

    @Test
    public void override_is_filtered() throws Exception {
        addToIndex(LogFileHelper.createBzippedLogFile(logDirectory, "20100101",
                "REQUEST FROM:127.0.0.1\n" +
                        "PARAMS:\n" +
                        "DATA=\n" +
                        "\n" +
                        "inet6num:      2001::/64\n" +
                        "source:        RIPE\n" +
                        "override: username,password,remark\n"));

        assertThat(getUpdates("2001::/64"), containsString("override: username, FILTERED, remark\n"));
    }

    // API calls

    private String getUpdates(final String searchTerm) throws IOException {
        return client
                .target(String.format("http://localhost:%s/api/logs?search=%s&fromdate=&todate=&apiKey=%s", jettyBootstrap.getPort(), URLEncoder.encode(searchTerm, "ISO-8859-1"), API_KEY))
                .request()
                .get(String.class);
    }

    private String getUpdates(final String searchTerm, final String date) throws IOException {
        return client
                .target(String.format("http://localhost:%s/api/logs?search=%s&fromdate=%s&apiKey=%s", jettyBootstrap.getPort(), URLEncoder.encode(searchTerm, "ISO-8859-1"), date, API_KEY))
                .request()
                .get(String.class);
    }

    private String getUpdates(final String searchTerm, final String fromDate, final String toDate) throws IOException {
        return client
                .target(String.format("http://localhost:%s/api/logs?search=%s&fromdate=%s&todate=%s&apiKey=%s", jettyBootstrap.getPort(), URLEncoder.encode(searchTerm, "ISO-8859-1"), fromDate, toDate, API_KEY))
                .request()
                .get(String.class);
    }

    // helper methods

    private void addToIndex(final File file) throws IOException {
        if (file.isDirectory()) {
            legacyLogFormatProcessor.addDirectoryToIndex(file.getAbsolutePath());
        } else {
            legacyLogFormatProcessor.addFileToIndex(file.getAbsolutePath());
        }
    }
}
