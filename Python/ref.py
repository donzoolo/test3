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
                    print("All search strings found, stopping
