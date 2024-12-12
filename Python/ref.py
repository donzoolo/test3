def extract_info_line_by_line(file_path, search_strings):
    extracted = {key: None for key in search_strings}  # Initialize results
    patterns = {
        search_string: re.compile(rf'{re.escape(search_string)}\s+BIC11(.*?)\n[A-Z]', re.DOTALL)
        for search_string in search_strings
    }
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            buffer = ""
            for line in file:
                print(f"Processing line: {line[:50]}...")  # Debug: Monitor progress
                buffer += line

                # Check if any search string exists in the buffer
                for search_string, pattern in patterns.items():
                    if extracted[search_string] is not None:
                        continue  # Skip already found strings

                    match = pattern.search(buffer)
                    if match:
                        after_bic11 = match.group(1)
                        last_string = re.findall(r'\S+', after_bic11)[-1]
                        extracted[search_string] = last_string
                        print(f"Found match for {search_string}: {last_string}")
                        buffer = ""  # Clear buffer after match
                        break

                # Stop early if all search strings are found
                if all(value is not None for value in extracted.values()):
                    print("All search strings found, stopping early.")
                    return extracted

                # Limit buffer size
                if len(buffer) > 10000:  # Adjust size as needed
                    buffer = buffer[-10000:]  # Keep only the last 10,000 characters

    except UnicodeDecodeError as e:
        print(f"Error reading file: {e}")
    return extracted
