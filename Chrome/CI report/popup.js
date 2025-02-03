document.getElementById("reportForm").addEventListener("submit", function(e) {
  e.preventDefault();

  const itUrl = document.getElementById("itUrl").value.trim();
  const atUrl = document.getElementById("atUrl").value.trim();
  const jiraNumber = document.getElementById("jiraNumber").value.trim();
  const releaseNumber = document.getElementById("releaseNumber").value.trim();

  // Prepare the data payload.
  const payload = { itUrl, atUrl, jiraNumber, releaseNumber };

  // Send a message to the background script to start processing.
  chrome.runtime.sendMessage({ action: "processReports", payload }, function(response) {
    console.log("Background response:", response);
    // Optionally display feedback in the popup.
    alert("Process started. Check your downloads and console logs for details.");
  });
});
