// Keep service worker alive during async operations
let keepAlive = false;
const keepAliveInterval = setInterval(() => {
    if (keepAlive) {
        console.log('Keeping service worker alive');
    }
}, 20000);

// Wait for tab to be fully loaded
function waitForTabLoad(tabId) {
    return new Promise((resolve, reject) => {
        chrome.tabs.onUpdated.addListener(function listener(updatedTabId, changeInfo) {
            if (updatedTabId === tabId && changeInfo.status === 'complete') {
                chrome.tabs.onUpdated.removeListener(listener);
                console.log(`Tab ${tabId} fully loaded`);
                resolve();
            }
        });
        setTimeout(() => reject(new Error('Tab load timeout')), 30000);
    });
}

// Capture screenshot via content script
function captureScreenshot(tabId) {
    return new Promise((resolve, reject) => {
        chrome.tabs.sendMessage(tabId, { action: 'captureScreenshot' }, (response) => {
            if (chrome.runtime.lastError) {
                reject(new Error(`Message error: ${chrome.runtime.lastError.message}`));
            } else if (response && response.success) {
                resolve(response.dataUrl);
            } else {
                reject(new Error(response?.error || 'Screenshot capture failed'));
            }
        });
    });
}

// Process individual page (IT or AT)
async function processPage(url, releaseNumber, jiraNumber, environmentValue, type) {
    let tabId;
    try {
        console.log(`Creating new tab for ${type} URL: ${url}`);
        const tab = await chrome.tabs.create({ url, active: true });
        tabId = tab.id;
        console.log(`Created tab ${tabId}`);
        await waitForTabLoad(tabId);

        const loadedTab = await chrome.tabs.get(tabId);
        console.log(`Tab ${tabId} URL after load: ${loadedTab.url}`);
        if (!loadedTab.url.startsWith(url)) {
            throw new Error(`Tab URL mismatch: expected ${url}, got ${loadedTab.url}`);
        }

        // Capture screenshot using content script
        console.log(`Capturing ${type} screenshot for tab ${tabId}`);
        const screenshotDataUrl = await captureScreenshot(tabId);
        const screenshotFilename = `${releaseNumber} ${environmentValue} ${jiraNumber} ${type}.png`;
        await chrome.downloads.download({
            url: screenshotDataUrl,
            filename: screenshotFilename,
            saveAs: false
        });
        console.log(`Downloaded screenshot: ${screenshotFilename}`);

        // Download report file
        const reportSuffix = type === 'it' ? 'zip/Allure_20Report.zip' : 'zip/Serenity_20Illustrated_20Report.zip';
        const reportUrl = `${loadedTab.url}${reportSuffix}`;
        const reportFilename = `${releaseNumber} ${environmentValue} ${jiraNumber} ${type} - ${type === 'it' ? 'Allure' : 'Serenity'}.zip`;
        
        await chrome.downloads.download({
            url: reportUrl,
            filename: reportFilename,
            saveAs: false
        });
        console.log(`Downloaded report: ${reportFilename}`);
    } catch (error) {
        console.error(`Error processing ${type} page:`, error);
        throw error;
    } finally {
        if (tabId) {
            chrome.tabs.remove(tabId).catch(err => console.error('Error closing tab:', err));
        }
    }
}

// Listen for messages from popup
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === 'processReports') {
        keepAlive = true;
        
        (async () => {
            try {
                const { itUrl, atUrl, releaseNumber, jiraNumber, environmentValue } = message.data;

                // Process IT page in a new tab
                await processPage(itUrl, releaseNumber, jiraNumber, environmentValue, 'it');
                
                // Process AT page in a new tab
                await processPage(atUrl, releaseNumber, jiraNumber, environmentValue, 'at');

                sendResponse({ success: true });
            } catch (error) {
                console.error('Error processing reports:', error);
                sendResponse({ success: false, error: error.message });
            } finally {
                keepAlive = false;
            }
        })();

        return true; // Indicate async response
    }
});