import re

# Define file paths
file_path = "your_new_file.txt"  # Replace with your new file path
file_path_old = "your_old_file.txt"  # Replace with your old file path

# Define the lists of input values
added = ["AAAABBBBXXX", "AAAABBBBXXY", "AAAABBBBX2X"]  # New entries
updated = ["AAAABBBBXXZ", "AAAABBBBX3X"]  # Entries to compare between old and new files

# Function to extract information for search strings in a file, line-by-line
def extract_info_line_by_line(file_path, search_strings):
    extracted = {key: None for key in search_strings}  # Initialize results
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            buffer = ""
            for line in file:
                buffer += line
                # Check if any search string exists in the buffer
                for search_string in search_strings:
                    if extracted[search_string] is not None:
                        continue  # Skip already found strings
                    pattern = rf'{re.escape(search_string)}\s+BIC11(.*?)\n[A-Z]'
                    match = re.search(pattern, buffer, re.DOTALL)
                    if match:
                        after_bic11 = match.group(1)
                        last_string = re.findall(r'\S+', after_bic11)[-1]
                        extracted[search_string] = last_string
                        buffer = ""  # Clear buffer after match
                        break
    except UnicodeDecodeError as e:
        print(f"Error reading file: {e}")
    return extracted

# Process 'added' entries
print("\nProcessing 'added' entries:")
added_results = extract_info_line_by_line(file_path, added)
for search_string, last_string in added_results.items():
    if last_string:
        print(f"Search string: {search_string} -> Last string: {last_string}")
    else:
        print(f"Search string: {search_string} -> No match found.")

# Process 'updated' entries
print("\nProcessing 'updated' entries:")
old_results = extract_info_line_by_line(file_path_old, updated)
new_results = extract_info_line_by_line(file_path, updated)

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
