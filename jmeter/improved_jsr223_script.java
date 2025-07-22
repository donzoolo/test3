import java.util.concurrent.ThreadLocalRandom

// Configuration maps for message types and parameters
final def MESSAGE_TYPES = [
    ISO: [
        [type: 'pacs.009.001.08', file: 'pacs009.xml', ratioVar: 'messagePercentageISOpacs009'],
        [type: 'pacs.008.001.08', file: 'pacs008.xml', ratioVar: 'messagePercentageISOpacs008', orderingFiRatioVar: 'orderingFiRolePercentageISOpacs008'],
        [type: 'pacs.004.001.10', file: 'pacs004.xml', ratioVar: 'messagePercentageISOpacs004']
    ],
    FIN: [
        [type: '103', file: 'mt103.txt', ratioVar: 'messagePercentageFINmt103', orderingFiRatioVar: 'orderingFiRolePercentageMt103'],
        [type: '202', file: 'mt202.txt', ratioVar: 'messagePercentageFINmt202'],
        [type: '202.COV', file: 'mt202C.txt', ratioVar: 'messagePercentageFINmt202C']
    ]
]

final def BLOCKING_CONFIG = [
    ISO: [currency: 'USD', amount: '200.00', creditor: '91990100', debtor: '1234567111'],
    FIN: [currency: 'USD', amount: '201,00', creditor: '91990100', debtor: '1234567111']
]

final def NON_BLOCKING_CONFIG = [
    ISO: [currency: 'INR', amount: '170.00', creditor: '99990100', debtor: '1234567111'],
    FIN: [currency: 'INR', amount: '169,00', creditor: '99990100', debtor: '1234567111']
]

final def NO_ALERT_CONFIG = [
    ISO: [currency: 'PLN', amount: '1.00', creditor: '99990100', debtor: '1234567121'],
    FIN: [currency: 'PLN', amount: '2,00', creditor: '99990100', debtor: '1234567121']
]

final def ACCOUNT_GENERATION = [
    suffix: '0000',
    minNumber: 1,
    maxNumber: 10,
    fallbackAccount: 'FALLBACK00001'
]

// Utility function to get random number
def randomInt(int min, int max) {
    ThreadLocalRandom.current().nextInt(min, max + 1)
}

// Utility function to get integer from vars with default and validation
def getIntVar(String key, int defaultValue) {
    try {
        def value = vars[key]?.toInteger() ?: defaultValue
        if (value < 0 || value > 100) {
            log.warn("Variable ${key} value ${value} out of range [0-100], using default: ${defaultValue}")
            return defaultValue
        }
        return value
    } catch (Exception e) {
        log.warn("Failed to parse ${key}: ${e.message}, using default: ${defaultValue}")
        return defaultValue
    }
}

// Utility function to set message variables
def setMessageVars(String prefix, Map config, String messageFile = null, String requestType = null) {
    vars.put("${prefix}currency", config.currency)
    vars.put("${prefix}amount", config.amount)
    vars.put("${prefix}account_creditor", config.creditor)
    vars.put("${prefix}account_debtor", config.debtor)
    if (messageFile) vars.put("${prefix}message_file", messageFile)
    if (requestType) vars.put("${prefix}request_type", requestType)
}

// Utility function to generate account number
def generateAccountNumber(String bic) {
    if (!ACCOUNT_GENERATION) {
        log.error("ACCOUNT_GENERATION map is undefined, using fallback account: ${ACCOUNT_GENERATION?.fallbackAccount ?: 'FALLBACK00001'}")
        return ACCOUNT_GENERATION?.fallbackAccount ?: 'FALLBACK00001'
    }
    if (!bic || bic.length() < 8) {
        log.warn("Invalid BIC for account generation: ${bic}, using fallback: ${ACCOUNT_GENERATION.fallbackAccount}")
        return ACCOUNT_GENERATION.fallbackAccount
    }
    def randomSuffix = randomInt(ACCOUNT_GENERATION.minNumber, ACCOUNT_GENERATION.maxNumber)
    def generatedAccount = "${bic}${ACCOUNT_GENERATION.suffix}${randomSuffix}"
    log.info("Generated account number: ${generatedAccount} from BIC: ${bic}")
    return generatedAccount
}

// Utility function to validate BIC
def validateBic(String bic) {
    if (!bic || bic.length() < 8 || !(bic ==~ /^[A-Z][A-Z0-9]{7,}/)) {
        log.error("Invalid BIC: ${bic}, using default: PDEFAULTXX")
        return 'PDEFAULTXX'
    }
    return bic
}

// Main logic
def randomNumbers = [
    finIso: randomInt(0, 99),
    messageType: randomInt(0, 99),
    orderingFi: randomInt(0, 99),
    blocking: randomInt(0, 99),
    accountGeneration: randomInt(0, 99)
]

def bic = validateBic(vars.get('bic'))
def newNotOrderingFiBic = 'O' + bic.substring(1)
vars.put('bic_deciding_my_role', newNotOrderingFiBic)
vars.put('no_alert_bic_deciding_my_role', newNotOrderingFiBic)
vars.put('randomNumberFinOrIso', randomNumbers.finIso.toString())

def finVsIsoRatio = getIntVar('FinVsIsoRatio', 50)
def blockingRatio = getIntVar('blockingAlertRatio', 50)
def accountGenerationRatio = getIntVar('blockingAccountGenerationRatio', 30)

def isIso = randomNumbers.finIso < finVsIsoRatio
def isBlocking = randomNumbers.blocking < blockingRatio
def messageGroup = isIso ? MESSAGE_TYPES.ISO : MESSAGE_TYPES.FIN
def configPrefix = isIso ? '' : 'fin_'
def configGroup = isBlocking ? BLOCKING_CONFIG : NON_BLOCKING_CONFIG
def noAlertGroup = NO_ALERT_CONFIG

// Set no-alert defaults
setMessageVars(isIso ? 'noalert_' : 'fin_noalert_', noAlertGroup[isIso ? 'ISO' : 'FIN'])

// Select message type
def selectedMessage = null
def cumulativeRatio = 0
for (msg in messageGroup) {
    cumulativeRatio += getIntVar(msg.ratioVar, 0)
    if (randomNumbers.messageType < cumulativeRatio) {
        selectedMessage = msg
        break
    }
}

if (!selectedMessage) {
    log.error("No message type selected for ${isIso ? 'ISO' : 'FIN'}, check ratio configurations")
    return
}

// Configure account numbers for blocking messages
def finalConfig = configGroup[isIso ? 'ISO' : 'FIN'].clone()
if (isBlocking && randomNumbers.accountGeneration < accountGenerationRatio) {
    def accountBic = randomInt(0, 1) == 0 ? bic : newNotOrderingFiBic
    def generatedAccount = generateAccountNumber(accountBic)
    finalConfig.creditor = generatedAccount
    finalConfig.debtor = generatedAccount // Same for creditor and debtor
}

// Set message-specific variables
def alertingMessage = isIso ? (isBlocking ? vars.get('pacs008_blocking') : vars.get('pacs008_nonblocking')) : 'none'
vars.put('alerting_message', alertingMessage)
setMessageVars(configPrefix, finalConfig, selectedMessage.file, selectedMessage.type)

log.info("# Sending BIC: ${bic} ${isBlocking ? 'BLOCKING' : 'NON_BLOCKING'} - ${selectedMessage.type}")

// Handle Ordering FI role
if (selectedMessage.orderingFiRatioVar && randomNumbers.orderingFi < getIntVar(selectedMessage.orderingFiRatioVar, 30)) {
    log.info("Setting Ordering FI role - bic_deciding_my_role: ${bic}")
    vars.put('bic_deciding_my_role', bic)
} else if (selectedMessage.orderingFiRatioVar) {
    log.info("Setting NOT Ordering FI role")
}

// Debug Groovy version
log.info("Groovy Version: ${GroovySystem.getVersion()}")