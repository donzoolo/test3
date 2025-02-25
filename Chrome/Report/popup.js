document.getElementById('reportForm').addEventListener('submit', function(e) {
    e.preventDefault();

    // Capture form inputs
    const itNumber = document.getElementById('itNumber').value;
    const atNumber = document.getElementById('atNumber').value;
    const jiraNumber = document.getElementById('jiraNumber').value;
    const releaseNumber = document.getElementById('releaseNumber').value;
    const environment = document.getElementById('environment').value;

    // Map environment to environment-value
    let environmentValue;
    switch (environment) {
        case 'local-ci':
            environmentValue = 'this-is-my-local-ci';
            break;
        case 'ci':
            environmentValue = 'this-time-different-url-ci';
            break;
        case 'tt':
            environmentValue = 'your-mapped-string-for-tt';
            break;
        case 'live':
            environmentValue = 'your-mapped-string-for-live';
            break;
        default:
            console.error('Invalid environment selected:', environment);
            throw new Error('Invalid environment selection');
    }

    // Construct URLs
    const itUrl = `https://www.testIt.com/${environmentValue}/${itNumber}/report/`;
    const atUrl = `https://www.testAt.com/${environmentValue}/${atNumber}/report/`;

    // Send message to background script
    chrome.runtime.sendMessage({
        action: 'processReports',
        data: {
            itUrl,
            atUrl,
            releaseNumber,
            jiraNumber
        }
    }, (response) => {
        if (chrome.runtime.lastError) {
            console.error('Error sending message:', chrome.runtime.lastError);
        } else {
            console.log('Message sent successfully:', response);
            window.close(); // Close popup after submission
        }
    });
});