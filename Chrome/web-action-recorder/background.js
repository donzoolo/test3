chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'takeScreenshot') {
    chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
      if (chrome.runtime.lastError || !tabs[0]) {
        console.error('No active tab found for screenshot');
        return;
      }
      const tab = tabs[0];
      const windowId = tab.windowId;
      console.log(`Attempting screenshot for tab ${tab.id}, window ${windowId}, URL: ${tab.url}, filename: ${msg.filename}`);
      chrome.tabs.captureVisibleTab(windowId, {format: 'png'}, (dataUrl) => {
        if (chrome.runtime.lastError || !dataUrl) {
          console.error('Screenshot capture failed');
          return;
        }
        const savePath = `web-action-recorder/${msg.filename}`;
        chrome.downloads.download({url: dataUrl, filename: savePath, conflictAction: 'uniquify'}, (downloadId) => {
          if (chrome.runtime.lastError) {
            console.error('Download failed');
          } else {
            console.log('Screenshot saved:', savePath);
          }
        });
      });
    });
  }
});