// Utility: wait for a given number of milliseconds.
function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Navigates the active tab to the given URL and resolves when the page load is complete.
 */
function updateTab(url) {
  return new Promise((resolve) => {
    chrome.tabs.update({ url: url }, (tab) => {
      chrome.tabs.onUpdated.addListener(function listener(tabId, changeInfo) {
        if (tabId === tab.id && changeInfo.status === "complete") {
          chrome.tabs.onUpdated.removeListener(listener);
          // Wait an extra second to allow for full rendering.
          delay(1000).then(() => resolve(tabId));
        }
      });
    });
  });
}

/**
 * Captures the visible area of the active tab.
 */
function captureTab() {
  return new Promise((resolve, reject) => {
    chrome.tabs.captureVisibleTab(null, { format: "png" }, (dataUrl) => {
      if (chrome.runtime.lastError) {
        return reject(chrome.runtime.lastError);
      }
      resolve(dataUrl);
    });
  });
}

/**
 * Initiates a download for the provided URL with the given filename.
 */
function downloadFile(fileUrl, filename) {
  chrome.downloads.download({
    url: fileUrl,
    filename: filename,
    saveAs: false
  });
}

/**
 * Processes the report tasks for one page.
 *
 * @param {string} url - The URL to navigate to.
 * @param {string} screenshotName - Filename for the screenshot.
 * @param {string} fileSuffix - The suffix (path fragment) to append to the current URL for the file download.
 * @param {string} fileDownloadName - Filename for the downloaded file.
 */
async function processPage(url, screenshotName, fileSuffix, fileDownloadName) {
  // Navigate to the URL.
  console.log(`Navigating to ${url} ...`);
  const tabId = await updateTab(url);
  
  // Wait a bit more if needed.
  await delay(1000);
  
  // Capture a screenshot.
  console.log(`Capturing screenshot and saving as ${screenshotName} ...`);
  const dataUrl = await captureTab();
  chrome.downloads.download({
    url: dataUrl,
    filename: screenshotName,
    saveAs: false
  });
  
  // Construct the file URL by appending the fileSuffix.
  // (Assumes the current URL is accessible via tab URL.)
  chrome.tabs.get(tabId, (tab) => {
    const currentUrl = tab.url;
    const fileUrl = currentUrl + fileSuffix;
    console.log(`Downloading file from ${fileUrl} as ${fileDownloadName} ...`);
    downloadFile(fileUrl, fileDownloadName);
  });

  // Optional: Wait before proceeding to the next task.
  await delay(2000);
}

// Listen for messages from the popup.
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.action === "processReports" && message.payload) {
    const { itUrl, atUrl, jiraNumber, releaseNumber } = message.payload;
    
    // Compose filenames using the provided release and JIRA numbers.
    const itScreenshotName = `${releaseNumber} XXX ${jiraNumber} it.png`;
    const itDownloadName = `${releaseNumber} XXX ${jiraNumber} it - Allure.zip`;
    const atScreenshotName = `${releaseNumber} XXX ${jiraNumber} at.png`;
    const atDownloadName = `${releaseNumber} XXX ${jiraNumber} at - Serenity.zip`;

    // File suffixes (to be appended to the current page URL).
    const itFileSuffix = "*zip*/Allure_20Report.zip";
    const atFileSuffix = "*zip*/Serenity_20Illustrated_20Report.zip";
    
    // Process tasks sequentially.
    (async () => {
      try {
        // Process the IT page.
        await processPage(itUrl, itScreenshotName, itFileSuffix, itDownloadName);
        // Process the AT page.
        await processPage(atUrl, atScreenshotName, atFileSuffix, atDownloadName);
        console.log("All tasks completed.");
        sendResponse({ status: "completed" });
      } catch (error) {
        console.error("Error processing tasks:", error);
        sendResponse({ status: "error", error: error.toString() });
      }
    })();

    // Return true to indicate asynchronous sendResponse.
    return true;
  }
});
