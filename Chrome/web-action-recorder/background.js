chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'takeScreenshot') {
    chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
      if (!tabs[0]) {
        console.error('No active tab found for screenshot');
        return;
      }
      chrome.tabs.captureVisibleTab(tabs[0].id, {format: 'png'}, (dataUrl) => {
        if (chrome.runtime.lastError) {
          console.error('Screenshot capture failed:', chrome.runtime.lastError.message);
          return;
        }
        if (!dataUrl) {
          console.error('No data URL returned from captureVisibleTab');
          return;
        }
        const filename = `web-action-recorder/${msg.filename}`;
        chrome.downloads.download({url: dataUrl, filename, conflictAction: 'uniquify'}, (downloadId) => {
          if (chrome.runtime.lastError) {
            console.error('Download failed for', filename, ':', chrome.runtime.lastError.message);
          } else {
            console.log('Screenshot saved:', filename);
          }
        });
      });
    });
  }
});