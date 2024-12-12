import re

# Define file paths
file_path = "your_new_file.txt"  # Replace with your new file path
file_path_old = "your_old_file.txt"  # Replace with your old file path

# Define the lists of input values
added = ["AAAABBBBXXX", "AAAABBBBXXY", "AAAABBBBX2X"]  # New entries
updated = ["AAAABBBBXXZ", "AAAABBBBX3X"]  # Entries to compare between old and new files

# Function to extract information from a file
def extract_info(file_path, search_strings):
    extracted = {}
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            content = file.read()

        # Preprocess the content to handle newlines appropriately
        content = re.sub(r'\n+', '\n', content)

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
                extracted[search_string] = last_string
            else:
                extracted[search_string] = None
    except UnicodeDecodeError as e:
        print(f"Error reading file: {e}")
    return extracted

# Extract information for "added" strings in the new file
print("\nProcessing 'added' entries:")
added_results = extract_info(file_path, added)
for search_string, last_string in added_results.items():
    if last_string:
        print(f"Search string: {search_string} -> Last string: {last_string}")
    else:
        print(f"Search string: {search_string} -> No match found.")

# Compare "updated" entries between old and new files
print("\nProcessing 'updated' entries:")
old_results = extract_info(file_path_old, updated)
new_results = extract_info(file_path, updated)

for search_string in updated:
    old_value = old_results.get(search_string, None)
    new_value = new_results.get(search_string, None)

    if old_value and new_value and old_value != new_value:
        print(f"Search string: {search_string} -> Changed from {old_value} to {new_value}")
    elif old_value == new_value:
        print(f"Search string: {search_string} -> No change (still {new_value})")
    elif not old_value and new_value:
        print(f"Search string: {search_string} -> Newly added in the new file as {new_value}")
    elif old_value and not new_value:
        print(f"Search string: {search_string} -> Removed in the new file (was {old_value})")
    else:
        print(f"Search string: {search_string} -> Not found in both files.")
