import re

# Define file paths
file_path = "your_new_file.txt"  # Replace with your new file path
file_path_old = "your_old_file.txt"  # Replace with your old file path

# Define the lists of input values
added = ["AAAABBBBXXX", "AAAABBBBXXY", "AAAABBBBX2X"]  # New entries
updated = ["AAAABBBBXXZ", "AAAABBBBX3X"]  # Entries to compare between old and new files

# Function to process file in chunks of 1000 entries
def extract_info_in_chunks(file_path, search_strings, chunk_size=1000):
    extracted = {key: None for key in search_strings}  # Initialize results
    # Update regex to match the new entry format
    patterns = {
        search_string: re.compile(
            rf'\nA.*?{re.escape(search_string)}\s+BIC11.*?([a-zA-Z_]+/[a-zA-Z_]+)\nA', re.DOTALL
        )
        for search_string in search_strings
    }

    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            entries = []
            buffer = ""
            entry_pattern = re.compile(r'\nA.*?\nA', re.DOTALL)  # Match entries

            for line in file:
                buffer += line
                # Extract entries from the buffer
                while True:
                    match = entry_pattern.search(buffer)
                    if not match:
                        break
                    entry = match.group()
                    entries.append(entry)
                    buffer = buffer[match.end():]  # Remove the processed entry from the buffer

                    # Process the chunk if we reach chunk size
                    if len(entries) >= chunk_size:
                        extracted.update(process_chunk(entries, patterns, search_strings))
                        entries = []  # Reset entries

            # Process remaining entries
            if entries:
                extracted.update(process_chunk(entries, patterns, search_strings))

    except UnicodeDecodeError as e:
        print(f"Error reading file: {e}")

    return extracted

# Function to process a chunk of entries
def process_chunk(entries, patterns, search_strings):
    results = {}
    for entry in entries:
        for search_string, pattern in patterns.items():
            if results.get(search_string) is not None:
                continue  # Skip already found strings
            if search_string in entry:  # Quick check before running regex
                match = pattern.search(entry)
                if match:
                    last_string = match.group(1)
                    results[search_string] = last_string
                    # Debug: Print the entire entry
                    print(f"Entry containing {search_string} ->\n{entry.strip()}")
                    print(f"Extracted last string: {last_string}")
    return results

# Process 'added' entries
print("\nProcessing 'added' entries:")
added_results = extract_info_in_chunks(file_path, added)
for search_string, last_string in added_results.items():
    if last_string:
        print(f"Search string: {search_string} -> Last string: {last_string}")
    else:
        print(f"Search string: {search_string} -> No match found.")

# Process 'updated' entries
print("\nProcessing 'updated' entries:")
old_results = extract_info_in_chunks(file_path_old, updated)
new_results = extract_info_in_chunks(file_path, updated)

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
