import re

file_path = "your_file.txt"  # Replace with your file path

# Define the list of input values to search for
search_strings = ["AAAABBBBXXX", "AAAABBBBXXY", "AAAABBBBX2X"]

# Read the file contents
with open(file_path, "r") as file:
    content = file.read()

# Preprocess the content to handle newlines appropriately
content = re.sub(r'\n+', '\n', content)

# Iterate over each search string
for search_string in search_strings:
    # Define a regex pattern dynamically for each search string
    pattern = rf'{re.escape(search_string)}\s+BIC11(.*?)\n[A-Z]'

    # Match the relevant content
    match = re.search(pattern, content, re.DOTALL)

    if match:
        # Extract the part between BIC11 and the newline followed by an uppercase letter
        after_bic11 = match.group(1)
        # Find the last non-whitespace string before the newline followed by an uppercase letter
        last_string = re.findall(r'\S+', after_bic11)[-1]
        print(f"Search string: {search_string} -> Last string: {last_string}")
    else:
        print(f"Search string: {search_string} -> No match found.")
