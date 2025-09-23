let actions = [];
let clickListener;
let scrollListener;
let isRecording = false;
let config = {maxDepth: 5, waitTimeout: 5000, postDelay: 500}; // Defaults

// Load config from storage
chrome.storage.sync.get('config', (data) => {
  if (data.config) config = data.config;
});

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'startRecording') {
    startRecording();
  } else if (msg.type === 'stopRecording') {
    stopRecording();
  } else if (msg.type === 'replay') {
    replay(msg.actions);
  }
});

/**
 * Starts recording clicks and scrolls.
 */
function startRecording() {
  if (isRecording) return;
  actions = [];
  isRecording = true;

  clickListener = (e) => {
    const locator = getLocator(e.target);
    const currentUrl = window.location.href;

    actions.push({action: 'click', locator});

    setTimeout(() => {
      if (window.location.href !== currentUrl) {
        actions.pop();
        actions.push({action: 'navigate', url: window.location.href});
        console.log('Detected navigation after click, recorded as navigate:', window.location.href);
      } else {
        console.log('No navigation, recorded as click:', locator);
      }
    }, 1500); // Increased for slower navigations
  };
  window.addEventListener('click', clickListener, true);

  const handleScroll = () => {
    actions.push({action: 'scroll', x: window.scrollX, y: window.scrollY});
    console.log('Recorded action: scroll', {x: window.scrollX, y: window.scrollY});
  };
  scrollListener = debounce(handleScroll, 500);
  window.addEventListener('scroll', scrollListener, true);
}

/**
 * Stops recording and sends actions.
 */
function stopRecording() {
  if (!isRecording) return;
  window.removeEventListener('click', clickListener, true);
  window.removeEventListener('scroll', scrollListener, true);
  isRecording = false;
  console.log('Recording stopped, actions:', actions);
  chrome.runtime.sendMessage({type: 'recordingData', actions: [...actions]});
}

/**
 * Replays actions with dynamic waits and error recovery.
 */
async function replay(replayActions) {
  console.log('Starting replay with actions:', replayActions);
  for (let i = 0; i < replayActions.length; i++) {
    const act = replayActions[i];
    try {
      console.log(`Executing action ${i + 1}:`, act);

      if (act.action === 'click') {
        const element = await waitForElement(act.locator, config.waitTimeout);
        if (element) {
          console.log('Clicking element:', act.locator);
          element.click();
        } else {
          console.error('Element not found or not visible for click:', act.locator);
        }
      } else if (act.action === 'navigate') {
        console.log('Navigating to:', act.url);
        window.location.href = act.url;
        await waitForPageLoad(config.waitTimeout);
      } else if (act.action === 'scroll') {
        console.log('Scrolling to:', {x: act.x, y: act.y});
        window.scrollTo(act.x, act.y);
      }

      await new Promise(resolve => setTimeout(resolve, config.postDelay));
      console.log(`Requesting screenshot: screenshot_${i + 1}_${new Date().toISOString().replace(/[:.]/g, '')}.png`);
      chrome.runtime.sendMessage({type: 'takeScreenshot', filename: `screenshot_${i + 1}_${new Date().toISOString().replace(/[:.]/g, '')}.png`});
    } catch (err) {
      console.error(`Error in action ${i + 1}:`, err);
      // Continue to next action
    }
  }
  console.log('Replay completed');
}

/**
 * Waits for element using MutationObserver.
 */
function waitForElement(locator, timeout) {
  return new Promise((resolve) => {
    let element = findElement(locator);
    if (element && isElementVisible(element)) {
      resolve(element);
      return;
    }

    const observer = new MutationObserver(() => {
      element = findElement(locator);
      if (element && isElementVisible(element)) {
        observer.disconnect();
        resolve(element);
      }
    });
    observer.observe(document.body, {childList: true, subtree: true});

    setTimeout(() => {
      observer.disconnect();
      resolve(null);
    }, timeout);
  });
}

/**
 * Finds element, handling shadow DOM.
 */
function findElement(locator) {
  let element = null;
  if (locator.type === 'css') {
    element = document.querySelector(locator.value) || document.querySelector('pierce/' + locator.value); // Basic shadow pierce
  } else if (locator.type === 'xpath') {
    element = document.evaluate(locator.value, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
  }
  return element;
}

/**
 * Waits for page load using MutationObserver on readyState.
 */
function waitForPageLoad(timeout) {
  return new Promise((resolve) => {
    if (document.readyState === 'complete') {
      resolve(true);
      return;
    }

    const observer = new MutationObserver(() => {
      if (document.readyState === 'complete') {
        observer.disconnect();
        resolve(true);
      }
    });
    observer.observe(document.documentElement, {childList: true, subtree: true});

    setTimeout(() => {
      observer.disconnect();
      console.error('Page load timeout');
      resolve(false);
    }, timeout);
  });
}

/**
 * Checks if element is visible.
 */
function isElementVisible(element) {
  if (!element) return false;
  const style = window.getComputedStyle(element);
  return style.display !== 'none' &&
         style.visibility !== 'hidden' &&
         style.opacity > 0 &&
         element.offsetParent !== null;
}

// Rest of the code (getLocator, getXPathOfElement, etc.) remains as in previous version
function getLocator(target) {
  const preferredAttrs = ['data-locator', 'data-testid', 'data-test-id', 'data-test', 'data-qa', 'data-cy', 'aria-label'];
  const actionableTags = ['A', 'BUTTON', 'INPUT', 'SELECT'];
  let current = target;
  let depth = 0;
  const maxDepth = config.maxDepth || 5;

  while (current && current.tagName !== 'BODY' && depth <= maxDepth) {
    if (actionableTags.includes(current.tagName.toUpperCase())) {
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
    }
    current = current.parentElement;
    depth++;
  }

  return {type: 'xpath', value: getXPathOfElement(target)};
}

function getXPathOfElement(elt) {
  let path = "";
  for (; elt && elt.nodeType === 1; elt = elt.parentNode) {
    let idx = getElementIdx(elt);
    let xname = elt.tagName.toLowerCase();
    let attrStr = '';
    if (elt.hasAttribute('data-locator')) {
      attrStr = `[@data-locator="${cssEscape(elt.getAttribute('data-locator'))}"]`;
    } else if (elt.id) {
      attrStr = `[@id="${cssEscape(elt.id)}"]`;
    } else if (idx > 1) {
      attrStr = `[${idx}]`;
    }
    path = "/" + xname + attrStr + path;
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