let actions = [];
let clickListener;
let scrollListener;
let isRecording = false;

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'startRecording') {
    startRecording();
  } else if (msg.type === 'stopRecording') {
    stopRecording();
  } else if (msg.type === 'replay') {
    replay(msg.actions);
  }
});

function startRecording() {
  if (isRecording) return;
  actions = [];
  isRecording = true;

  clickListener = (e) => {
    const locator = getLocator(e.target);
    actions.push({action: 'click', locator});
    console.log('Recorded action: click', locator);
  };
  window.addEventListener('click', clickListener, true);

  const handleScroll = () => {
    actions.push({action: 'scroll', x: window.scrollX, y: window.scrollY});
    console.log('Recorded action: scroll', {x: window.scrollX, y: window.scrollY});
  };
  scrollListener = debounce(handleScroll, 500);
  window.addEventListener('scroll', scrollListener, true);
}

function stopRecording() {
  if (!isRecording) return;
  window.removeEventListener('click', clickListener, true);
  window.removeEventListener('scroll', scrollListener, true);
  isRecording = false;
  console.log('Recording stopped, actions:', actions);
  chrome.runtime.sendMessage({type: 'recordingData', actions: [...actions]});
}

async function replay(replayActions) {
  console.log('Starting replay with actions:', replayActions);
  for (let i = 0; i < replayActions.length; i++) {
    const act = replayActions[i];
    let element = null;

    console.log(`Executing action ${i + 1}:`, act);

    if (act.action === 'click') {
      if (act.locator.type === 'css') {
        try {
          element = document.querySelector(act.locator.value);
        } catch (e) {
          console.error('Invalid CSS selector:', act.locator.value, e);
        }
      } else if (act.locator.type === 'xpath') {
        element = document.evaluate(act.locator.value, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
      }
      if (element) {
        console.log('Clicking element:', act.locator);
        element.click();
      } else {
        console.error('Element not found for click:', act.locator);
      }
    } else if (act.action === 'scroll') {
      console.log('Scrolling to:', {x: act.x, y: act.y});
      window.scrollTo(act.x, act.y);
    }

    // Increased wait to avoid screenshot quota limit
    await new Promise(resolve => setTimeout(resolve, 2500));

    // Take screenshot
    console.log(`Requesting screenshot: screenshot_${i + 1}.png`);
    chrome.runtime.sendMessage({type: 'takeScreenshot', filename: `screenshot_${i + 1}.png`});
  }
  console.log('Replay completed');
}

function getLocator(target) {
  const preferredAttrs = ['data-locator', 'data-testid', 'data-test-id', 'data-test', 'data-qa', 'data-cy'];
  let current = target;

  while (current && current.tagName !== 'BODY') {
    for (let attr of preferredAttrs) {
      if (current.hasAttribute(attr)) {
        return {type: 'css', value: `[${attr}="${cssEscape(current.getAttribute(attr))}"]`};
      }
    }

    const dataAttrs = Array.from(current.attributes).filter(a => a.name.startsWith('data-') && !preferredAttrs.includes(a.name));
    if (dataAttrs.length > 0) {
      dataAttrs.sort((a, b) => a.name.localeCompare(b.name));
      const attr = dataAttrs[0];
      return {type: 'css', value: `[${attr.name}="${cssEscape(attr.value)}"]`};
    }

    if (current.id) {
      return {type: 'css', value: `[id="${cssEscape(current.id)}"]`};
    }

    current = current.parentElement;
  }

  return {type: 'xpath', value: getXPathOfElement(target)};
}

function getXPathOfElement(elt) {
  let path = "";
  for (; elt && elt.nodeType === 1; elt = elt.parentNode) {
    let idx = getElementIdx(elt);
    let xname = elt.tagName.toLowerCase();
    if (idx > 1) xname += "[" + idx + "]";
    path = "/" + xname + path;
  }
  return path ? "/" + path : "";
}

function getElementIdx(elt) {
  let count = 1;
  for (let sib = elt.previousSibling; sib; sib = sib.previousSibling) {
    if (sib.nodeType === 1 && sib.tagName === elt.tagName) count++;
  }
  return count;
}

function debounce(func, wait) {
  let timeout;
  return (...args) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), wait);
  };
}

function cssEscape(value) {
  if (typeof CSS !== 'undefined' && CSS.escape) {
    return CSS.escape(value);
  }
  return value.replace(/([\\|!#$%&'()*+,./:;<=>?@[\]^{}`~])/g, '\\$1')
              .replace(/^(\d)/, '\\3$1 ');
}