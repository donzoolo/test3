chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'takeScreenshot') {
    // Use sender.tab.id as a fallback
    const fallbackTabId = sender.tab ? sender.tab.id : null;
    attemptScreenshot(msg.filename, fallbackTabId, 0);
  }
});

function attemptScreenshot(filename, fallbackTabId, attemptCount, maxAttempts = 3) {
  chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
    if (chrome.runtime.lastError) {
      console.error(`Tabs query failed: ${chrome.runtime.lastError.message}`);
      if (fallbackTabId && attemptCount < maxAttempts - 1) {
        console.log(`Retrying with fallback tab ID ${fallbackTabId} (attempt ${attemptCount + 2}/${maxAttempts})`);
        setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts), 1000);
      } else {
        console.error('Max attempts reached or no fallback tab ID, screenshot failed');
      }
      return;
    }
    if (!tabs[0]) {
      console.error(`No active tab found for screenshot (attempt ${attemptCount + 1}/${maxAttempts})`);
      if (fallbackTabId && attemptCount < maxAttempts - 1) {
        console.log(`Retrying with fallback tab ID ${fallbackTabId} (attempt ${attemptCount + 2}/${maxAttempts})`);
        setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts), 1000);
      } else {
        console.error('Max attempts reached or no fallback tab ID, screenshot failed');
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
          setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts), 1000);
        } else {
          console.error('Max attempts reached or no fallback tab ID, screenshot failed');
        }
        return;
      }
      if (!dataUrl) {
        console.error(`No data URL returned from captureVisibleTab for tab ${tabId}`);
        if (fallbackTabId && attemptCount < maxAttempts - 1) {
          console.log(`Retrying with fallback tab ID ${fallbackTabId} (attempt ${attemptCount + 2}/${maxAttempts})`);
          setTimeout(() => tryWithFallbackTabId(filename, fallbackTabId, attemptCount + 1, maxAttempts), 1000);
        } else {
          console.error('Max attempts reached or no fallback tab ID, screenshot failed');
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
      });
    });
  });
}

function tryWithFallbackTabId(filename, tabId, attemptCount, maxAttempts) {
  console.log(`Attempting screenshot with fallback tab ID ${tabId}, filename: ${filename} (attempt ${attemptCount + 1}/${maxAttempts})`);
  chrome.tabs.get(tabId, (tab) => {
    if (chrome.runtime.lastError || !tab) {
      console.error(`Fallback tab ${tabId} is invalid: ${chrome.runtime.lastError ? chrome.runtime.lastError.message : 'Tab not found'}`);
      if (attemptCount < maxAttempts - 1) {
        setTimeout(() => attemptScreenshot(filename, null, attemptCount + 1, maxAttempts), 1000);
      } else {
        console.error('Max attempts reached, screenshot failed');
      }
      return;
    }
    console.log(`Fallback tab valid, URL: ${tab.url}`);
    chrome.tabs.captureVisibleTab(tabId, {format: 'png'}, (dataUrl) => {
      if (chrome.runtime.lastError) {
        console.error(`Screenshot capture failed for fallback tab ${tabId}: ${chrome.runtime.lastError.message}`);
        if (attemptCount < maxAttempts - 1) {
          setTimeout(() => attemptScreenshot(filename, null, attemptCount + 1, maxAttempts), 1000);
        } else {
          console.error('Max attempts reached, screenshot failed');
        }
        return;
      }
      if (!dataUrl) {
        console.error(`No data URL returned from captureVisibleTab for fallback tab ${tabId}`);
        if (attemptCount < maxAttempts - 1) {
          setTimeout(() => attemptScreenshot(filename, null, attemptCount + 1, maxAttempts), 1000);
        } else {
          console.error('Max attempts reached, screenshot failed');
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
      });
    });
  });
}