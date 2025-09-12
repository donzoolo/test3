chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.action === 'captureScreenshot') {
    chrome.tabs.captureVisibleTab(null, {format: 'png'}, (dataUrl) => {
      const filename = `screenshot_${message.pageUrl.replace(/[^a-zA-Z0-9]/g, '_')}.png`;
      chrome.downloads.download({
        url: dataUrl,
        filename: filename,
        saveAs: false
      });
      sendResponse({success: true});
    });
    return true;
  }
});