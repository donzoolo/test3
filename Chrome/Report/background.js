// Keep service worker alive during async operations
let keepAlive = false;
const keepAliveInterval = setInterval(() => {
    if (keepAlive) {
        console.log('Keeping service worker alive');
    }
}, 20000);

// Wait for tab to be fully loaded
function waitForTabLoad(tabId) {
    return new Promise((resolve) => {
        chrome.tabs.onUpdated.addListener(function listener(updatedTabId, changeInfo) {
            if (updatedTabId === tabId && changeInfo.status === 'complete') {
                chrome.tabs.onUpdated.removeListener(listener);
                resolve();
            }
        });
    });
}

// Process individual page (IT or AT)
async function processPage(tabId, url, releaseNumber, jiraNumber, type) {
    try {
        // Update tab to target URL
        await chrome.tabs.update(tabId, { url });
        await waitForTabLoad(tabId);
        console.log(`Loaded ${type} page: ${url}`);

        // Capture screenshot
        try {
            const screenshotDataUrl = await chrome.tabs.captureVisibleTab(null, { format: 'png' });
            if (!screenshotDataUrl) {
                throw new Error('Failed to capture screenshot');
            }
            const screenshotFilename = `${releaseNumber} XXX ${jiraNumber} ${type}.png`;
            await chrome.downloads.download({
                url: screenshotDataUrl,
                filename: screenshotFilename,
                saveAs: false
            });
            console.log(`Downloaded screenshot: ${screenshotFilename}`);
        } catch (screenshotError) {
            console.error(`Failed to capture ${type} screenshot:`, screenshotError);
            throw screenshotError;
        }

        // Get current tab URL and download report file
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (!tab?.url) {
            throw new Error('Could not get tab URL');
        }
        const reportSuffix = type === 'it' ? 'zip/Allure_20Report.zip' : 'zip/Serenity_20Illustrated_20Report.zip';
        const reportUrl = `${tab.url}${reportSuffix}`;
        const reportFilename = `${releaseNumber} XXX ${jiraNumber} ${type} - ${type === 'it' ? 'Allure' : 'Serenity'}.zip`;
        
        await chrome.downloads.download({
            url: reportUrl,
            filename: reportFilename,
            saveAs: false
        });
        console.log(`Downloaded report: ${reportFilename}`);
    } catch (error) {
        console.error(`Error processing ${type} page:`, error);
        throw error;
    }
}

// Listen for messages from popup
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === 'processReports') {
        keepAlive = true;
        
        (async () => {
            try {
                const { itUrl, atUrl, releaseNumber, jiraNumber } = message.data;
                const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
                if (!tab) {
                    throw new Error('No active tab found');
                }

                // Process IT page
                await processPage(tab.id, itUrl, releaseNumber, jiraNumber, 'it');
                
                // Process AT page
                await processPage(tab.id, atUrl, releaseNumber, jiraNumber, 'at');

                sendResponse({ success: true });
            } catch (error) {
                console.error('Error processing reports:', error);
                sendResponse({ success: false, error: error.message });
            } finally {
                keepAlive = false;
            }
        })();

        // Return true to indicate async response
        return true;
    }
});