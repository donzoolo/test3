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
  };
  window.addEventListener('click', clickListener, true);

  const handleScroll = () => {
    actions.push({action: 'scroll', x: window.scrollX, y: window.scrollY});
  };
  scrollListener = debounce(handleScroll, 500);
  window.addEventListener('scroll', scrollListener, true);
}

function stopRecording() {
  if (!isRecording) return;
  window.removeEventListener('click', clickListener, true);
  window.removeEventListener('scroll', scrollListener, true);
  isRecording = false;
  chrome.runtime.sendMessage({type: 'recordingData', actions: [...actions]});
}

async function replay(replayActions) {
  for (let i = 0; i < replayActions.length; i++) {
    const act = replayActions[i];
    let element = null;

    if (act.action === 'click') {
      if (act.locator.type === 'css') {
        element = document.querySelector(act.locator.value);
      } else if (act.locator.type === 'xpath') {
        element = document.evaluate(act.locator.value, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
      }
      if (element) {
        element.click();
      } else {
        console.error('Element not found for click:', act.locator);
      }
    } else if (act.action === 'scroll') {
      window.scrollTo(act.x, act.y);
    }

    // Fixed wait
    await new Promise(resolve => setTimeout(resolve, 1500));

    // Take screenshot
    chrome.runtime.sendMessage({type: 'takeScreenshot', filename: `screenshot_${i + 1}.png`});
  }
}

function getLocator(el) {
  const preferredAttrs = ['data-testid', 'data-test-id', 'data-test', 'data-qa', 'data-cy'];
  for (let attr of preferredAttrs) {
    if (el.hasAttribute(attr)) {
      return {type: 'css', value: `[${attr}="${el.getAttribute(attr)}"]`};
    }
  }

  const dataAttrs = Array.from(el.attributes).filter(a => a.name.startsWith('data-') && !preferredAttrs.includes(a.name));
  if (dataAttrs.length > 0) {
    dataAttrs.sort((a, b) => a.name.localeCompare(b.name));
    const attr = dataAttrs[0];
    return {type: 'css', value: `[${attr.name}="${attr.value}"]`};
  }

  if (el.id) {
    return {type: 'css', value: `#${el.id}`};
  }

  return {type: 'xpath', value: getXPathOfElement(el)};
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