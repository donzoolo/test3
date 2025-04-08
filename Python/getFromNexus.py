import requests
import json
from requests.auth import HTTPBasicAuth

class NexusHelper:
    def __init__(self, nexus_url, username, password):
        """
        Initialize the NexusHelper with connection details.
        
        Args:
            nexus_url (str): The base URL of the Nexus instance
            username (str): Nexus username
            password (str): Nexus password
        """
        self.nexus_url = nexus_url
        self.username = username
        self.password = password

    def fetch_nexus_components(self, repository, search_params=None):
        """
        Fetch components from Sonatype Nexus using REST API v1 with pagination.
        
        Args:
            repository (str): The name of the repository to search in
            search_params (dict): Optional dictionary of additional search parameters
            
        Returns:
            list: List of all component items fetched across all pages
        """
        endpoint = f"{self.nexus_url}/service/rest/v1/search"
        
        params = {"repository": repository}
        if search_params:
            params.update(search_params)
        
        all_items = []
        continuation_token = None
        
        try:
            while True:
                if continuation_token:
                    params["continuationToken"] = continuation_token
                
                print(f"Requesting: {endpoint} with params: {params}")
                response = requests.get(
                    endpoint,
                    auth=HTTPBasicAuth(self.username, self.password),
                    params=params,
                    headers={"Accept": "application/json"},
                    timeout=30
                )
                response.raise_for_status()
                
                result = response.json()
                items = result.get("items", [])
                print(f"Response: Found {len(items)} items in this page")
                
                if items:
                    all_items.extend(items)
                    print(f"Total so far: {len(all_items)}")
                else:
                    print("No more items found.")
                    break
                
                continuation_token = result.get("continuationToken")
                if not continuation_token:
                    print("No more pages to fetch.")
                    break
                    
            return all_items
            
        except requests.exceptions.HTTPError as http_err:
            print(f"HTTP error occurred: {http_err} - Response: {http_err.response.text}")
            return None
        except requests.exceptions.RequestException as err:
            print(f"Error occurred: {err}")
            return None

    def extract_component_info(self, components):
        """
        Extract name, downloadUrl, path, and sha256 from components.
        
        Args:
            components (list): List of component dictionaries
            
        Returns:
            list: List of dictionaries with extracted fields
        """
        extracted_info = []
        
        for component in components:
            name = component.get("name", "N/A")
            for asset in component.get("assets", []):
                info = {
                    "name": name,
                    "downloadUrl": asset.get("downloadUrl", "N/A"),
                    "path": asset.get("path", "N/A"),
                    "sha256": asset.get("checksum", {}).get("sha256", "N/A")
                }
                extracted_info.append(info)
        
        return extracted_info

    def get_multiple_components(self, repository, search_params_list):
        """
        Fetch and extract info for multiple components.
        
        Args:
            repository (str): The name of the repository
            search_params_list (list): List of search parameter dictionaries
            
        Returns:
            list: List of extracted component info
        """
        all_component_info = []
        
        for params in search_params_list:
            print(f"\nFetching components for: {params}")
            components = self.fetch_nexus_components(repository, params)
            
            if components:
                component_info = self.extract_component_info(components)
                all_component_info.extend(component_info)
            else:
                print(f"No components found for {params}")
        
        return all_component_info

# Example usage
def main():
    # Nexus connection details
    nexus_url = "http://localhost:8081"  # Replace with your Nexus instance URL
    username = "admin"                   # Replace with your Nexus username
    password = "admin123"               # Replace with your Nexus password
    
    # Instantiate the helper
    nexus_helper = NexusHelper(nexus_url, username, password)
    
    # Specify the repository
    target_repo = "docker-internal"
    
    # Define three different component searches
    search_params_list = [
        {"q": "my-app", "version": "1.0.0", "format": "docker"},
        {"q": "web-service", "version": "2.1.3", "format": "docker"},
        {"q": "database", "version": "latest", "format": "docker"}
    ]
    
    # Fetch and extract components
    all_component_info = nexus_helper.get_multiple_components(target_repo, search_params_list)
    
    # Print the results
    if all_component_info:
        print(f"\nTotal components extracted: {len(all_component_info)}")
        for info in all_component_info:
            print(json.dumps(info, indent=2))

if __name__ == "__main__":
    main()

# Example of using the class from another module
"""
# In another file (e.g., other_script.py)
from nexus_helper import NexusHelper

def some_other_function():
    nexus_helper = NexusHelper("http://localhost:8081", "admin", "admin123")
    repo = "docker-internal"
    params = {"q": "my-app", "version": "1.0.0", "format": "docker"}
    components = nexus_helper.fetch_nexus_components(repo, params)
    info = nexus_helper.extract_component_info(components)
    print(info)
"""