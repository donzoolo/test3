import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportTest extends ItTest {

    @Autowired
    private AuditLogService auditLogService;

    private LocalDateTime beforeTestDate;

    @BeforeEach
    void setup() {
        // Set beforeTestDate with a 5-second buffer
        this.beforeTestDate = LocalDateTime.now().minusSeconds(5);
    }

    @Test
    void downloadReportTest() {
        // Step 1: Retrieve audit log entries before action
        List<AuditLog> initialAuditLogs = auditLogService.findEntriesAfter(beforeTestDate);

        // Step 2: Perform the action (e.g., download report)
        downloadReport(); // Implement the download action here

        // Step 3: Retrieve audit log entries after action
        List<AuditLog> finalAuditLogs = auditLogService.findEntriesAfter(beforeTestDate);

        // Step 4: Filter out entries present in the initial list
        List<AuditLog> newAuditLogs = finalAuditLogs.stream()
                .filter(entry -> !initialAuditLogs.contains(entry))
                .collect(Collectors.toList());

        // Step 5: Assert that only one new entry is present
        assertEquals(1, newAuditLogs.size(), "Expected only one new audit log entry");

        // Step 6: Verify the content of the new audit log entry
        AuditLog newEntry = newAuditLogs.get(0);
        assertEquals("Download Report", newEntry.getAction(), "Audit log entry action does not match expected value");
        assertTrue(newEntry.getDetails().contains("Report downloaded successfully"), "Audit log entry details do not match expected content");
    }

    private void downloadReport() {
        // Logic to perform the report download action goes here
    }
}


import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class AuditLogUtils {

    @Autowired
    private AuditLogService auditLogService;

    /**
     * Retrieves all audit log entries after a given date.
     *
     * @param date The date after which to retrieve the audit log entries.
     * @return A list of audit log entries.
     */
    public List<AuditLog> getAuditLogsAfter(LocalDateTime date) {
        return auditLogService.findEntriesAfter(date);
    }

    /**
     * Filters out old audit log entries from the final list based on the initial list.
     *
     * @param initialLogs The list of audit log entries before the action.
     * @param finalLogs The list of audit log entries after the action.
     * @return A list of new audit log entries.
     */
    public List<AuditLog> getNewAuditLogs(List<AuditLog> initialLogs, List<AuditLog> finalLogs) {
        return finalLogs.stream()
                .filter(entry -> !initialLogs.contains(entry))
                .collect(Collectors.toList());
    }

    /**
     * Verifies that only one new audit log entry is present and matches expected action and details.
     *
     * @param newAuditLogs The list of new audit log entries.
     * @param expectedAction The expected action of the new audit log entry.
     * @param expectedDetails A part of the expected details to verify.
     */
    public void verifySingleAuditLogEntry(List<AuditLog> newAuditLogs, String expectedAction, String expectedDetails) {
        if (newAuditLogs.size() != 1) {
            throw new AssertionError("Expected only one new audit log entry, but found " + newAuditLogs.size());
        }
        AuditLog newEntry = newAuditLogs.get(0);
        if (!newEntry.getAction().equals(expectedAction)) {
            throw new AssertionError("Audit log entry action does not match expected value. Expected: " 
                    + expectedAction + ", but was: " + newEntry.getAction());
        }
        if (!newEntry.getDetails().contains(expectedDetails)) {
            throw new AssertionError("Audit log entry details do not match expected content. Expected to contain: " 
                    + expectedDetails);
        }
    }
}
