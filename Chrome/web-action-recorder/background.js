chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'takeScreenshot') {
    attemptScreenshot(msg.filename);
  }
});

async function attemptScreenshot(filename) {
  return new Promise((resolve) => {
    chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
      if (chrome.runtime.lastError) {
        console.error(`Tabs query failed: ${chrome.runtime.lastError.message}`);
        resolve();
        return;
      }
      if (!tabs[0]) {
        console.error(`No active tab found for screenshot`);
        resolve();
        return;
      }
      const tab = tabs[0];
      const tabId = tab.id;
      const windowId = tab.windowId;
      console.log(`Attempting screenshot for tab ${tabId}, window ${windowId}, URL: ${tab.url}, filename: ${filename}`);
      chrome.tabs.captureVisibleTab(windowId, {format: 'png'}, (dataUrl) => {
        if (chrome.runtime.lastError) {
          console.error(`Screenshot capture failed for window ${windowId}: ${chrome.runtime.lastError.message}`);
          resolve();
          return;
        }
        if (!dataUrl) {
          console.error(`No data URL returned from captureVisibleTab for window ${windowId}`);
          resolve();
          return;
        }
        const savePath = `web-action-recorder/${filename}`;
        chrome.downloads.download({url: dataUrl, filename: savePath, conflictAction: 'uniquify'}, (downloadId) => {
          if (chrome.runtime.lastError) {
            console.error(`Download failed for ${savePath}: ${chrome.runtime.lastError.message}`);
          } else {
            console.log(`Screenshot saved: ${savePath}`);
          }
          // Wait 1 second to avoid quota issues
          setTimeout(resolve, 1000);
        });
      });
    });
  });
}