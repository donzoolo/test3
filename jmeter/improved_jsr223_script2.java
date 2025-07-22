import java.util.concurrent.ThreadLocalRandom;

// Configuration constants for better maintainability
class MessageConfig {
    static final String DEFAULT_NOT_ORDERING_FI_PREFIX = "O";
    static final String DEFAULT_CURRENCY_NON_ALERT = "PLN";
    static final String DEFAULT_CURRENCY_BLOCKING = "USD";
    static final String DEFAULT_CURRENCY_NON_BLOCKING = "INR";
    static final String DEFAULT_AMOUNT_NON_ALERT = "1.00";
    static final String DEFAULT_AMOUNT_BLOCKING = "200.00";
    static final String DEFAULT_AMOUNT_NON_BLOCKING = "170.00";
    static final String FIN_DEFAULT_AMOUNT_NON_ALERT = "2,00";
    static final String FIN_DEFAULT_AMOUNT_BLOCKING = "201,00";
    static final String FIN_DEFAULT_AMOUNT_NON_BLOCKING = "169,00";
    
    // Account generation constants
    static final String ACCOUNT_SUFFIX = "0000";
    static final int MIN_ACCOUNT_NUMBER = 1;
    static final int MAX_ACCOUNT_NUMBER = 10;
    static final String DEFAULT_CREDITOR_BLOCKING = "91990100";
    static final String DEFAULT_DEBTOR_BLOCKING = "1234567111";
    static final String DEFAULT_CREDITOR_NON_BLOCKING = "99990100";
    static final String DEFAULT_DEBTOR_NON_BLOCKING = "1234567111";
    static final String DEFAULT_CREDITOR_NON_ALERT = "99990100";
    static final String DEFAULT_DEBTOR_NON_ALERT = "1234567121";
}

// Generate all random numbers at once for better organization
int randomFinIso = ThreadLocalRandom.current().nextInt(100);
int randomMessageType = ThreadLocalRandom.current().nextInt(100);
int randomOrderingFIMt103 = ThreadLocalRandom.current().nextInt(100);
int randomOrderingFIPacs008 = ThreadLocalRandom.current().nextInt(100);
int randomRatio = ThreadLocalRandom.current().nextInt(100);
int randomAccountGeneration = ThreadLocalRandom.current().nextInt(100);

// Extract configuration values with validation
int blockingAlertRatio = getIntVar("blockingAlertRatio", 50);
int finVsIsoRatio = getIntVar("FinVsIsoRatio", 50);
int generatedAccountRatio = getIntVar("generatedAccountRatio", 30); // New parameter for account generation

// ISO message ratios
int pacs004Ratio = getIntVar("messagePercentageISOpacs004", 5);
int pacs008Ratio = getIntVar("messagePercentageISOpacs008", 45);
int pacs009Ratio = getIntVar("messagePercentageISOpacs009", 50);

// FIN message ratios
int mt103Ratio = getIntVar("messagePercentageFINmt103", 5);
int mt202Ratio = getIntVar("messagePercentageFINmt202", 45);
int mt202CRatio = getIntVar("messagePercentageFINmt202C", 50);

// Role percentages
int orderingFiPacs008Percentage = getIntVar("orderingFiRolePercentageISOpacs008", 30);
int orderingFiMt103Percentage = getIntVar("orderingFiRolePercentageMt103", 30);

// Helper method to safely get integer variables with defaults
int getIntVar(String key, int defaultValue) {
    try {
        return vars[key] != null ? vars[key].toInteger() : defaultValue;
    } catch (Exception e) {
        log.warn("Failed to parse variable '${key}', using default value: ${defaultValue}");
        return defaultValue;
    }
}

// Generate BIC for non-ordering FI role
String currentBic = vars.get("bic");
if (currentBic == null || currentBic.isEmpty()) {
    log.error("BIC variable is null or empty");
    return;
}

String notOrderingFiBic = MessageConfig.DEFAULT_NOT_ORDERING_FI_PREFIX + currentBic.substring(1);

// Initialize default values
initializeDefaultValues(notOrderingFiBic);

// Store random number for potential debugging
vars.put("randomNumberFinOrIso", randomFinIso.toString());

log.info("Processing BIC: ${currentBic}, Random FIN/ISO: ${randomFinIso}, Threshold: ${finVsIsoRatio}");

// Main logic: Choose between ISO and FIN
if (randomFinIso < finVsIsoRatio) {
    processIsoMessages(randomRatio, blockingAlertRatio, randomMessageType, 
                      pacs009Ratio, pacs008Ratio, randomOrderingFIPacs008, 
                      orderingFiPacs008Percentage, currentBic, randomAccountGeneration, generatedAccountRatio);
} else {
    processFinMessages(randomRatio, blockingAlertRatio, randomMessageType,
                      mt103Ratio, mt202Ratio, randomOrderingFIMt103,
                      orderingFiMt103Percentage, currentBic, randomAccountGeneration, generatedAccountRatio);
}

// Helper method to initialize default values
void initializeDefaultValues(String notOrderingFiBic) {
    // Set default BIC roles
    vars.put("bic_deciding_my_role", notOrderingFiBic);
    vars.put("no_alert_bic_deciding_my_role", notOrderingFiBic);
    
    // ISO non-alert defaults
    vars.put("noalert_currency", MessageConfig.DEFAULT_CURRENCY_NON_ALERT);
    vars.put("noalert_amount", MessageConfig.DEFAULT_AMOUNT_NON_ALERT);
    vars.put("noalert_account_creditor", MessageConfig.DEFAULT_CREDITOR_NON_ALERT);
    vars.put("noalert_account_debtor", MessageConfig.DEFAULT_DEBTOR_NON_ALERT);
    
    // FIN non-alert defaults
    vars.put("fin_noalert_currency", MessageConfig.DEFAULT_CURRENCY_NON_ALERT);
    vars.put("fin_noalert_amount", MessageConfig.FIN_DEFAULT_AMOUNT_NON_ALERT);
    vars.put("fin_noalert_account_creditor", MessageConfig.DEFAULT_CREDITOR_NON_ALERT);
    vars.put("fin_noalert_account_debtor", MessageConfig.DEFAULT_DEBTOR_NON_ALERT);
}

// Generate account number based on BIC + 0000 + random number (1-10)
String generateAccountNumber(String bic) {
    if (bic == null || bic.length() < 8) {
        log.warn("Invalid BIC for account generation: ${bic}, using fallback");
        return "FALLBACK000001";
    }
    
    // Use first 8 characters of BIC
    String bicPrefix = bic.substring(0, 8).toUpperCase();
    
    // Generate random account suffix (1-10) with proper padding
    int accountNumber = ThreadLocalRandom.current().nextInt(
        MessageConfig.MIN_ACCOUNT_NUMBER, 
        MessageConfig.MAX_ACCOUNT_NUMBER + 1
    );
    
    String accountSuffix = String.format("%02d", accountNumber);
    String generatedAccount = bicPrefix + MessageConfig.ACCOUNT_SUFFIX + accountSuffix;
    
    log.info("Generated account number: ${generatedAccount} from BIC: ${bic}");
    return generatedAccount;
}

// Determine account numbers for blocking messages
AccountNumbers getBlockingAccountNumbers(String currentBic, String notOrderingFiBic, 
                                        int randomAccountGeneration, int generatedAccountRatio) {
    if (randomAccountGeneration < generatedAccountRatio) {
        // Generate accounts using algorithm
        String pAccount = generateAccountNumber(currentBic);
        String oAccount = generateAccountNumber(notOrderingFiBic);
        
        // For blocking messages, creditor and debtor must be the same when using generated accounts
        log.info("Using generated accounts - P-BIC account: ${pAccount}, O-BIC account: ${oAccount}");
        return new AccountNumbers(pAccount, pAccount); // Same account for creditor and debtor
    } else {
        // Use existing default accounts
        log.info("Using default blocking accounts");
        return new AccountNumbers(MessageConfig.DEFAULT_CREDITOR_BLOCKING, MessageConfig.DEFAULT_DEBTOR_BLOCKING);
    }
}

// Determine account numbers for non-blocking messages
AccountNumbers getNonBlockingAccountNumbers() {
    // Non-blocking messages always use default accounts
    return new AccountNumbers(MessageConfig.DEFAULT_CREDITOR_NON_BLOCKING, MessageConfig.DEFAULT_DEBTOR_NON_BLOCKING);
}

// Helper class to hold account numbers
class AccountNumbers {
    String creditor;
    String debtor;
    
    AccountNumbers(String creditor, String debtor) {
        this.creditor = creditor;
        this.debtor = debtor;
    }
}

// Process ISO messages
void processIsoMessages(int randomRatio, int blockingRatio, int messageType,
                       int pacs009Ratio, int pacs008Ratio, int randomOrderingFI,
                       int orderingFiPercentage, String currentBic, 
                       int randomAccountGeneration, int generatedAccountRatio) {
    
    boolean isBlocking = randomRatio < blockingRatio;
    String logPrefix = "ISO " + (isBlocking ? "BLOCKING" : "NON_BLOCKING");
    
    // Generate BIC for not ordering FI role
    String notOrderingFiBic = MessageConfig.DEFAULT_NOT_ORDERING_FI_PREFIX + currentBic.substring(1);
    
    // Set currency and amount based on blocking status
    if (isBlocking) {
        setIsoBlockingValues(currentBic, notOrderingFiBic, randomAccountGeneration, generatedAccountRatio);
    } else {
        setIsoNonBlockingValues();
    }
    
    // Determine message type
    if (messageType < pacs009Ratio) {
        processIsoMessage("PACS.009", "pacs009.xml", "pacs.009.001.08", logPrefix, currentBic, isBlocking);
    } else if (messageType < pacs009Ratio + pacs008Ratio) {
        processIsoPacs008Message(logPrefix, currentBic, isBlocking, randomOrderingFI, orderingFiPercentage);
    } else {
        processIsoMessage("PACS.004", "pacs004.xml", "pacs.004.001.10", logPrefix, currentBic, isBlocking);
    }
}

// Process FIN messages
void processFinMessages(int randomRatio, int blockingRatio, int messageType,
                       int mt103Ratio, int mt202Ratio, int randomOrderingFI,
                       int orderingFiPercentage, String currentBic,
                       int randomAccountGeneration, int generatedAccountRatio) {
    
    boolean isBlocking = randomRatio < blockingRatio;
    String logPrefix = "FIN " + (isBlocking ? "BLOCKING" : "NON_BLOCKING");
    
    // Generate BIC for not ordering FI role
    String notOrderingFiBic = MessageConfig.DEFAULT_NOT_ORDERING_FI_PREFIX + currentBic.substring(1);
    
    // Set currency and amount based on blocking status
    if (isBlocking) {
        setFinBlockingValues(currentBic, notOrderingFiBic, randomAccountGeneration, generatedAccountRatio);
    } else {
        setFinNonBlockingValues();
    }
    
    // Determine message type
    if (messageType < mt103Ratio) {
        processFinMt103Message(logPrefix, currentBic, randomOrderingFI, orderingFiPercentage);
    } else if (messageType < mt103Ratio + mt202Ratio) {
        processFinMessage("MT 202", "mt202.txt", "202", logPrefix, currentBic);
    } else {
        processFinMessage("MT 202 COV", "mt202C.txt", "202.COV", logPrefix, currentBic);
    }
}

// ISO message processing helpers
void setIsoBlockingValues(String currentBic, String notOrderingFiBic, 
                         int randomAccountGeneration, int generatedAccountRatio) {
    vars.put("currency", MessageConfig.DEFAULT_CURRENCY_BLOCKING);
    vars.put("amount", MessageConfig.DEFAULT_AMOUNT_BLOCKING);
    
    // Determine account numbers based on configuration
    AccountNumbers accounts = getBlockingAccountNumbers(currentBic, notOrderingFiBic, 
                                                       randomAccountGeneration, generatedAccountRatio);
    vars.put("account_creditor", accounts.creditor);
    vars.put("account_debtor", accounts.debtor);
}

void setIsoNonBlockingValues() {
    vars.put("currency", MessageConfig.DEFAULT_CURRENCY_NON_BLOCKING);
    vars.put("amount", MessageConfig.DEFAULT_AMOUNT_NON_BLOCKING);
    
    // Non-blocking always uses default accounts
    AccountNumbers accounts = getNonBlockingAccountNumbers();
    vars.put("account_creditor", accounts.creditor);
    vars.put("account_debtor", accounts.debtor);
}

void processIsoMessage(String messageTypeName, String filename, String requestType, 
                      String logPrefix, String currentBic, boolean isBlocking) {
    log.info("Sending BIC: ${currentBic} ${logPrefix} - ${messageTypeName}");
    vars.put("message_file", filename.toString());
    vars.put("request_type", requestType.toString());
    vars.put("alerting_message", vars.get(isBlocking ? "pacs008_blocking" : "pacs008_nonblocking"));
}

void processIsoPacs008Message(String logPrefix, String currentBic, boolean isBlocking,
                             int randomOrderingFI, int orderingFiPercentage) {
    log.info("Sending BIC: ${currentBic} ${logPrefix} - PACS.008");
    vars.put("message_file", "pacs008.xml".toString());
    vars.put("request_type", "pacs.008.001.08".toString());
    vars.put("alerting_message", vars.get(isBlocking ? "pacs008_blocking" : "pacs008_nonblocking"));
    
    handleOrderingFiRole(randomOrderingFI, orderingFiPercentage, currentBic);
}

// FIN message processing helpers
void setFinBlockingValues(String currentBic, String notOrderingFiBic,
                         int randomAccountGeneration, int generatedAccountRatio) {
    vars.put("fin_currency", MessageConfig.DEFAULT_CURRENCY_BLOCKING);
    vars.put("fin_amount", MessageConfig.FIN_DEFAULT_AMOUNT_BLOCKING);
    
    // Determine account numbers based on configuration
    AccountNumbers accounts = getBlockingAccountNumbers(currentBic, notOrderingFiBic,
                                                       randomAccountGeneration, generatedAccountRatio);
    vars.put("fin_account_creditor", accounts.creditor);
    vars.put("fin_account_debtor", accounts.debtor);
}

void setFinNonBlockingValues() {
    vars.put("fin_currency", MessageConfig.DEFAULT_CURRENCY_NON_BLOCKING);
    vars.put("fin_amount", MessageConfig.FIN_DEFAULT_AMOUNT_NON_BLOCKING);
    
    // Non-blocking always uses default accounts
    AccountNumbers accounts = getNonBlockingAccountNumbers();
    vars.put("fin_account_creditor", accounts.creditor);
    vars.put("fin_account_debtor", accounts.debtor);
}

void processFinMessage(String messageTypeName, String filename, String requestType,
                      String logPrefix, String currentBic) {
    log.info("Sending BIC: ${currentBic} ${logPrefix} - ${messageTypeName}");
    vars.put("fin_message_file", filename.toString());
    vars.put("fin_request_type", requestType.toString());
}

void processFinMt103Message(String logPrefix, String currentBic, int randomOrderingFI, int orderingFiPercentage) {
    log.info("Sending BIC: ${currentBic} ${logPrefix} - MT 103");
    vars.put("fin_message_file", "mt103.txt".toString());
    vars.put("fin_request_type", "103".toString());
    
    handleOrderingFiRole(randomOrderingFI, orderingFiPercentage, currentBic);
}

// Common helper for handling ordering FI role
void handleOrderingFiRole(int randomOrderingFI, int orderingFiPercentage, String currentBic) {
    if (randomOrderingFI < orderingFiPercentage) {
        log.info("Setting Ordering FI role - bic_deciding_my_role: ${currentBic}");
        vars.put("bic_deciding_my_role", currentBic);
    } else {
        log.info("Setting NOT Ordering FI role");
        // bic_deciding_my_role already set to notOrderingFiBic in initialization
    }
}