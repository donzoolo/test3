import re

# Function to extract information for search strings in a file
def extract_info_optimized(file_path, search_strings):
    extracted = {key: None for key in search_strings}  # Initialize results
    patterns = {
        search_string: re.compile(rf'{re.escape(search_string)}\s+BIC11(.*?)\n[A-Z]', re.DOTALL)
        for search_string in search_strings
    }

    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            buffer = ""
            found_count = 0  # Track how many strings have been found

            for line in file:
                # Accumulate lines in the buffer
                buffer += line

                # Check if any search string exists in the buffer
                for search_string, pattern in patterns.items():
                    if extracted[search_string] is not None:
                        continue  # Skip already found strings

                    if search_string in buffer:  # Quick check before running regex
                        match = pattern.search(buffer)
                        if match:
                            after_bic11 = match.group(1)
                            last_string = re.findall(r'\S+', after_bic11)[-1]
                            extracted[search_string] = last_string
                            found_count += 1
                            print(f"Found match for {search_string}: {last_string}")

                # Exit early if all strings are found
                if found_count == len(search_strings):
                    print("All search strings found, stopping early.")
                    return extracted

                # Flush buffer to avoid excessive memory usage
                if len(buffer) > 10000:
                    buffer = buffer[-10000:]  # Keep only the last 10,000 characters

    except UnicodeDecodeError as e:
        print(f"Error reading file: {e}")

    return extracted


# Example usage
file_path = "your_large_file.txt"  # Replace with your file path
search_strings = ["AAAABBBBXXX", "AAAABBBBXXY", "AAAABBBBX2X"]

results = extract_info_optimized(file_path, search_strings)
for search_string, last_string in results.items():
    if last_string:
        print(f"Search string: {search_string} -> Last string: {last_string}")
    else:
        print(f"Search string: {search_string} -> No match found.")
