import csv

# Define file paths
file_path = "your_new_file.txt"  # Replace with your new file path
file_path_old = "your_old_file.txt"  # Replace with your old file path

# Define the lists of input values
added = ["AAAABBBBXXX", "AAAABBBBXXY", "AAAABBBBX2X"]  # New entries
updated = ["AAAABBBBXXZ", "AAAABBBBX3X"]  # Entries to compare between old and new files

# Function to extract information from a file
def extract_info_from_csv(file_path, search_strings):
    extracted = {key: None for key in search_strings}  # Initialize results

    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            reader = csv.reader(file, delimiter="\t")  # Read as tab-separated values

            for row in reader:
                if len(row) < 49:
                    continue  # Skip lines with insufficient columns

                # Check if the first column is 'A' and search string is in column 14
                if row[0] == "A":
                    for search_string in search_strings:
                        if search_string == row[13] and row[14] == "BIC11":
                            # Extract value from column 49
                            extracted[search_string] = row[48]
                            print(f"Found match for {search_string}: {row[48]}")
    except UnicodeDecodeError as e:
        print(f"Error reading file: {e}")

    return extracted

# Process 'added' entries
print("\nProcessing 'added' entries:")
added_results = extract_info_from_csv(file_path, added)
for search_string, last_string in added_results.items():
    if last_string:
        print(f"Search string: {search_string} -> Last string: {last_string}")
    else:
        print(f"Search string: {search_string} -> No match found.")

# Process 'updated' entries
print("\nProcessing 'updated' entries:")
old_results = extract_info_from_csv(file_path_old, updated)
new_results = extract_info_from_csv(file_path, updated)

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
