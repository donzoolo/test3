from atlassian import Jira, Confluence
import html
from datetime import datetime

# Initialize Jira and Confluence clients
jira = Jira(
    url='https://your-jira-instance.atlassian.net',
    username='your-username',
    password='your-api-token'
)

confluence = Confluence(
    url='https://your-confluence-instance.atlassian.net',
    username='your-username',
    password='your-api-token'
)

# Step 1: Fetch Jira issues for the release
jql = 'project = ABC and fixVersion = "1.2.3"'  # Customize your JQL query
issues = jira.jql(jql)

# Step 2: Extract relevant fields from each issue
issue_data = []
for issue in issues['issues']:
    issue_data.append({
        'key': issue['key'],
        'summary': issue['fields']['summary'],
        'status': issue['fields']['status']['name']
        # Add more fields like 'assignee': issue['fields']['assignee']['displayName'] if needed
    })

# Step 3: Construct an HTML table
table_html = '<table><tr><th>Key</th><th>Summary</th><th>Status</th></tr>'
jira_base_url = 'https://your-jira-instance.atlassian.net/browse/'
for data in issue_data:
    key = html.escape(data['key'])  # Escape special characters for safety
    summary = html.escape(data['summary'])
    status = html.escape(data['status'])
    table_html += f'<tr><td><a href="{jira_base_url}{key}">{key}</a></td><td>{summary}</td><td>{status}</td></tr>'
table_html += '</table>'

# Step 4: Create the Confluence page with the static table
space = 'YOUR_SPACE'
parent_id = '123456'  # Optional: ID of the parent page
title = 'Release Note for Version 1.2.3'
timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
body = f'<h1>Release Note</h1><p>List of issues as of {timestamp}:</p>{table_html}<p>Other sections of the release note go here...</p>'

confluence.create_page(
    space=space,
    title=title,
    body=body,
    parent_id=parent_id
)

print(f"Release note created with static issue list: {title}")