let actionsToReplay = null;

document.getElementById('start').addEventListener('click', () => {
  chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
    chrome.tabs.sendMessage(tabs[0].id, {type: 'startRecording'});
    updateStatus('Recording started');
  });
});

document.getElementById('stop').addEventListener('click', () => {
  chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
    chrome.tabs.sendMessage(tabs[0].id, {type: 'stopRecording'});
    updateStatus('Stopping recording...');
  });
});

document.getElementById('fileInput').addEventListener('change', (event) => {
  const file = event.target.files[0];
  if (file) {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        actionsToReplay = JSON.parse(e.target.result);
        updateStatus('JSON loaded successfully');
      } catch (err) {
        updateStatus('Error parsing JSON');
      }
    };
    reader.readAsText(file);
  }
});

document.getElementById('replay').addEventListener('click', () => {
  if (!actionsToReplay) {
    updateStatus('Load JSON first');
    return;
  }
  chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
    chrome.tabs.sendMessage(tabs[0].id, {type: 'replay', actions: actionsToReplay});
    updateStatus('Replaying...');
  });
});

document.getElementById('saveConfig').addEventListener('click', () => {
  const config = {
    maxDepth: parseInt(document.getElementById('maxDepth').value, 10),
    waitTimeout: parseInt(document.getElementById('waitTimeout').value, 10),
    postDelay: parseInt(document.getElementById('postDelay').value, 10),
  };
  chrome.storage.sync.set({config}, () => {
    updateStatus('Config saved');
  });
});

// Load saved config on open
chrome.storage.sync.get('config', (data) => {
  const config = data.config || {maxDepth: 5, waitTimeout: 5000, postDelay: 500};
  document.getElementById('maxDepth').value = config.maxDepth;
  document.getElementById('waitTimeout').value = config.waitTimeout;
  document.getElementById('postDelay').value = config.postDelay;
});

chrome.runtime.onMessage.addListener((msg) => {
  if (msg.type === 'recordingData') {
    const json = JSON.stringify(msg.actions, null, 2);
    const blob = new Blob([json], {type: 'application/json'});
    const url = URL.createObjectURL(blob);
    chrome.downloads.download({url, filename: 'actions.json'});
    updateStatus('Recording downloaded as actions.json');
  }
});

function updateStatus(text) {
  document.getElementById('status').textContent = text;
}