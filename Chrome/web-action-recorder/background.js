let screenshotQueue = [];
let isProcessingQueue = false;

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'takeScreenshot') {
    // Queue the screenshot request
    screenshotQueue.push({filename: msg.filename, tabId: sender.tab ? sender.tab.id : null});
    console.log(`Queued screenshot: ${msg.filename}, queue length: ${screenshotQueue.length}`);
    processScreenshotQueue();
  }
});

async function processScreenshotQueue() {
  if (isProcessingQueue || screenshotQueue.length === 0) return;
  isProcessingQueue = true;

  const {filename, tabId: fallbackTabId} = screenshotQueue.shift();
  console.log(`Processing screenshot: ${filename}, queue remaining: ${screenshotQueue.length}`);
  await attemptScreenshot(filename, fallbackTabId, 0);

  // Ensure 1-second gap between screenshot requests to avoid quota limit
  await new Promise(resolve => setTimeout(resolve, 1000));
  isProcessingQueue = false;
  processScreenshotQueue();
}

async function attemptScreenshot(filename, fallbackTabId, attemptCount, maxAttempts = 3) {
  return new Promise((resolve) => {
    chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
      if (chrome.runtime.lastError) {
        console.error(`Tabs query failed: ${chrome.runtime.lastError.message}`);
        if (fallbackTabId && attemptCount < maxAttempts - 1) {
          console.log(`Retrying with fallback tab ID ${fallbackTabId} (attempt ${attemptCount + 2}/${maxAttempts})`);
          setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts).then(resolve), 1000);
        } else {
          console.error('Max attempts reached or no fallback tab ID, screenshot failed');
          resolve();
        }
        return;
      }
      if (!tabs[0]) {
        console.error(`No active tab found for screenshot (attempt ${attemptCount + 1}/${maxAttempts})`);
        if (fallbackTabId && attemptCount < maxAttempts - 1) {
          console.log(`Retrying with fallback tab ID ${fallbackTabId} (attempt ${attemptCount + 2}/${maxAttempts})`);
          setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts).then(resolve), 1000);
        } else {
          console.error('Max attempts reached or no fallback tab ID, screenshot failed');
          resolve();
        }
        return;
      }
      const tab = tabs[0];
      const tabId = tab.id;
      console.log(`Attempting screenshot for tab ${tabId}, URL: ${tab.url}, filename: ${filename} (attempt ${attemptCount + 1}/${maxAttempts})`);
      chrome.tabs.captureVisibleTab(tabId, {format: 'png'}, (dataUrl) => {
        if (chrome.runtime.lastError) {
          console.error(`Screenshot capture failed for tab ${tabId}: ${chrome.runtime.lastError.message}`);
          if (fallbackTabId && attemptCount < maxAttempts - 1) {
            console.log(`Retrying with fallback tab ID ${fallbackTabId} (attempt ${attemptCount + 2}/${maxAttempts})`);
            setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts).then(resolve), 1000);
          } else {
            console.error('Max attempts reached or no fallback tab ID, screenshot failed');
            resolve();
          }
          return;
        }
        if (!dataUrl) {
          console.error(`No data URL returned from captureVisibleTab for tab ${tabId}`);
          if (fallbackTabId && attemptCount < maxAttempts - 1) {
            console.log(`Retrying with fallback tab ID ${fallbackTabId} (attempt ${attemptCount + 2}/${maxAttempts})`);
            setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts).then(resolve), 1000);
          } else {
            console.error('Max attempts reached or no fallback tab ID, screenshot failed');
            resolve();
          }
          return;
        }
        const savePath = `web-action-recorder/${filename}`;
        chrome.downloads.download({url: dataUrl, filename: savePath, conflictAction: 'uniquify'}, (downloadId) => {
          if (chrome.runtime.lastError) {
            console.error(`Download failed for ${savePath}: ${chrome.runtime.lastError.message}`);
          } else {
            console.log(`Screenshot saved: ${savePath}`);
          }
          resolve();
        });
      });
    });
  });
}

async function tryWithFallbackTabId(filename, tabId, attemptCount, maxAttempts) {
  return new Promise((resolve) => {
    console.log(`Attempting screenshot with fallback tab ID ${tabId}, filename: ${filename} (attempt ${attemptCount + 1}/${maxAttempts})`);
    chrome.tabs.get(tabId, (tab) => {
      if (chrome.runtime.lastError || !tab) {
        console.error(`Fallback tab ${tabId} is invalid: ${chrome.runtime.lastError ? chrome.runtime.lastError.message : 'Tab not found'}`);
        if (attemptCount < maxAttempts - 1) {
          setTimeout(() => attemptScreenshot(filename, null, attemptCount + 1, maxAttempts).then(resolve), 1000);
        } else {
          console.error('Max attempts reached, screenshot failed');
          resolve();
        }
        return;
      }
      console.log(`Fallback tab valid, URL: ${tab.url}`);
      chrome.tabs.captureVisibleTab(tabId, {format: 'png'}, (dataUrl) => {
        if (chrome.runtime.lastError) {
          console.error(`Screenshot capture failed for fallback tab ${tabId}: ${chrome.runtime.lastError.message}`);
          if (attemptCount < maxAttempts - 1) {
            setTimeout(() => attemptScreenshot(filename, null, attemptCount + 1, maxAttempts).then(resolve), 1000);
          } else {
            console.error('Max attempts reached, screenshot failed');
            resolve();
          }
          return;
        }
        if (!dataUrl) {
          console.error(`No data URL returned from captureVisibleTab for fallback tab ${tabId}`);
          if (attemptCount < maxAttempts - 1) {
            setTimeout(() => attemptScreenshot(filename, null, attemptCount + 1, maxAttempts).then(resolve), 1000);
          } else {
            console.error('Max attempts reached, screenshot failed');
            resolve();
          }
          return;
        }
        const savePath = `web-action-recorder/${filename}`;
        chrome.downloads.download({url: dataUrl, filename: savePath, conflictAction: 'uniquify'}, (downloadId) => {
          if (chrome.runtime.lastError) {
            console.error(`Download failed for ${savePath}: ${chrome.runtime.lastError.message}`);
          } else {
            console.log(`Screenshot saved: ${savePath}`);
          }
          resolve();
        });
      });
    });
  });
}