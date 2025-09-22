chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'takeScreenshot') {
    attemptScreenshot(msg.filename, 0);
  }
});

function attemptScreenshot(filename, attemptCount, maxAttempts = 3) {
  chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
    if (chrome.runtime.lastError) {
      console.error(`Tabs query failed: ${chrome.runtime.lastError.message}`);
      return;
    }
    if (!tabs[0]) {
      console.error(`No active tab found for screenshot (attempt ${attemptCount + 1}/${maxAttempts})`);
      if (attemptCount < maxAttempts - 1) {
        setTimeout(() => attemptScreenshot(filename, attemptCount + 1, maxAttempts), 500);
      } else {
        console.error('Max attempts reached, screenshot failed');
      }
      return;
    }
    const tabId = tabs[0].id;
    console.log(`Attempting screenshot for tab ${tabId}, filename: ${filename} (attempt ${attemptCount + 1}/${maxAttempts})`);
    chrome.tabs.captureVisibleTab(tabId, {format: 'png'}, (dataUrl) => {
      if (chrome.runtime.lastError) {
        console.error(`Screenshot capture failed for tab ${tabId}: ${chrome.runtime.lastError.message}`);
        if (attemptCount < maxAttempts - 1) {
          setTimeout(() => attemptScreenshot(filename, attemptCount + 1, maxAttempts), 500);
        } else {
          console.error('Max attempts reached, screenshot failed');
        }
        return;
      }
      if (!dataUrl) {
        console.error(`No data URL returned from captureVisibleTab for tab ${tabId}`);
        if (attemptCount < maxAttempts - 1) {
          setTimeout(() => attemptScreenshot(filename, attemptCount + 1, maxAttempts), 500);
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