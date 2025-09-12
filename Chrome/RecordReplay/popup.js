// popup.js
document.getElementById('startRecord').addEventListener('click', () => {
  chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
    chrome.tabs.sendMessage(tabs[0].id, {action: 'startRecording'});
  });
});

document.getElementById('stopRecord').addEventListener('click', () => {
  chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
    chrome.tabs.sendMessage(tabs[0].id, {action: 'stopRecording'});
  });
});

document.getElementById('replay').addEventListener('click', () => {
  document.getElementById('upload').click();
});

document.getElementById('upload').addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (file) {
    const reader = new FileReader();
    reader.onload = (evt) => {
      const sequence = JSON.parse(evt.target.result);
      chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
        chrome.tabs.sendMessage(tabs[0].id, {action: 'replay', sequence});
      });
    };
    reader.readAsText(file);
  }
});
