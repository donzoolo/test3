chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'takeScreenshot') {
    chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
      chrome.tabs.captureVisibleTab(tabs[0].id, {format: 'png'}, (dataUrl) => {
        if (chrome.runtime.lastError) {
          console.error(chrome.runtime.lastError);
          return;
        }
        chrome.downloads.download({url: dataUrl, filename: msg.filename});
      });
    });
  }
});