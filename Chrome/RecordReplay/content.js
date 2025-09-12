let recording = [];
let isRecording = false;
let isReplaying = false;

// Helper: Get a reliable selector for an element (XPath for robustness in Angular)
function getSelector(el) {
  if (el.id) return `#${el.id}`;
  let path = '';
  while (el && el.nodeType === 1) {
    let sib = el, idx = 1;
    while (sib = sib.previousElementSibling) idx++;
    path = ` > ${el.tagName.toLowerCase()}:nth-child(${idx})${path}`;
    el = el.parentNode;
  }
  return `html${path.slice(3)}`; // Simple CSS-like path
}
// Choose the best locator for an element
function getLocator(element) {
  if (element.hasAttribute("data-locator")) {
    return { type: "data-locator", value: element.getAttribute("data-locator") };
  } else if (element.id) {
    return { type: "id", value: element.id };
  } else {
    return { type: "xpath", value: getXPath(element) };
  }
}

// Simple XPath generator (fallback only)
function getXPath(element) {
  if (element === document.body) {
    return '/html/body';
  }
  let ix = 0;
  const siblings = element.parentNode ? element.parentNode.childNodes : [];
  for (let i = 0; i < siblings.length; i++) {
    const sibling = siblings[i];
    if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
      ix++;
      if (sibling === element) {
        return getXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + ix + ']';
      }
    }
  }
}

// Helper: Wait for DOM stability (Angular-friendly)
function waitForStable(timeout = 5000) {
  return new Promise(resolve => {
    const observer = new MutationObserver(() => {
      clearTimeout(fallback);
      resolve();
    });
    observer.observe(document.body, { childList: true, subtree: true, attributes: true });
    const fallback = setTimeout(() => {
      observer.disconnect();
      resolve();
    }, timeout);
  });
}

// Helper: Capture screenshot with description
async function captureScreenshot(description) {
  const pageUrl = window.location.pathname.replace(/[^a-zA-Z0-9]/g, '_');
  chrome.runtime.sendMessage({
    action: 'captureScreenshot',
    pageUrl: `${pageUrl}_${description.replace(/[^a-zA-Z0-9]/g, '_')}`
  });
}

// Record event handler
function recordEvent(event) {
  if (!isRecording) return;
  const type = event.type;
  if (['click', 'change', 'keydown'].includes(type)) { // Add more event types as needed
    const selector = getSelector(event.target);
    const details = {};
    if (type === 'change') {
      details.value = event.target.value;
    } else if (type === 'keydown') {
      details.key = event.key;
    }
    recording.push({ type, selector, details });
    console.log(`Recorded: ${type} on ${selector}`);
  }
}

// Start recording
function startRecording() {
  isRecording = true;
  recording = [];
  document.addEventListener('click', recordEvent, true);
  document.addEventListener('change', recordEvent, true);
  document.addEventListener('keydown', recordEvent, true);
  console.log('Recording started...');
}

// Stop recording and download JSON
function stopRecording() {
  isRecording = false;
  document.removeEventListener('click', recordEvent, true);
  document.removeEventListener('change', recordEvent, true);
  document.removeEventListener('keydown', recordEvent, true);
  const blob = new Blob([JSON.stringify(recording, null, 2)], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  chrome.downloads.download({url, filename: 'recording.json', saveAs: false});
  console.log('Recording stopped and downloaded.');
}

// Replay the sequence
async function replaySequence(seq) {
  isReplaying = true;
  for (let i = 0; i < seq.length; i++) {
    const action = seq[i];
    console.log(`Replaying step ${i + 1}: ${action.type} on ${action.selector}`);
    const el = document.querySelector(action.selector);
    if (!el) {
      console.error(`Element not found: ${action.selector}`);
      continue;
    }
    let event;
    if (action.type === 'click') {
      event = new MouseEvent('click', {bubbles: true});
    } else if (action.type === 'change') {
      el.value = action.details.value;
      event = new Event('change', {bubbles: true});
    } else if (action.type === 'keydown') {
      event = new KeyboardEvent('keydown', {key: action.details.key, bubbles: true});
    }
    el.dispatchEvent(event);
    await waitForStable(); // Wait for Angular to update
    await captureScreenshot(`step_${i + 1}_${action.type}`); // Screenshot after each action
  }
  isReplaying = false;
  console.log('Replay complete!');
}

// Listen for messages from popup
chrome.runtime.onMessage.addListener((message) => {
  if (message.action === 'startRecording') startRecording();
  else if (message.action === 'stopRecording') stopRecording();
  else if (message.action === 'replay' && message.sequence) replaySequence(message.sequence);
});