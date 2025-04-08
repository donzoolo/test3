import requests
from requests.auth import HTTPBasicAuth

def get_asset_attributes(nexus_url, username, password, repo, asset_path):
    """
    Fetches the attributes of an asset in a Nexus repository by its path.

    Parameters:
    - nexus_url: str, base URL of the Nexus instance (e.g., 'http://localhost:8081')
    - username: str, username for authentication
    - password: str, password for authentication
    - repo: str, name of the repository
    - asset_path: str, path of the asset to fetch

    Returns:
    - dict, attributes of the asset if found, else raises an exception
    """
    auth = HTTPBasicAuth(username, password)
    url = f"{nexus_url}/service/rest/v1/assets?repository={repo}"
    while True:
        response = requests.get(url, auth=auth)
        if response.status_code != 200:
            raise Exception(f"Error: {response.status_code} {response.text}")
        data = response.json()
        if 'items' not in data:
            raise Exception("Invalid response format")
        for item in data['items']:
            if item['path'] == asset_path:
                return item
        continuationToken = data.get('continuationToken')
        if not continuationToken:
            break
        url = f"{nexus_url}/service/rest/v1/assets?continuationToken={continuationToken}&repository={repo}"
    raise Exception("Asset not found")

# Example usage
if __name__ == "__main__":
    nexus_url = "http://localhost:8081"
    username = "admin"
    password = "admin123"
    repo = "my-raw-repo"
    asset_path = "path/to/my/asset.txt"
    try:
        asset = get_asset_attributes(nexus_url, username, password, repo, asset_path)
        print("Asset found:")
        for key, value in asset.items():
            print(f"{key}: {value}")
    except Exception as e:
        print(f"Error: {e}")
        
        
import requests
from requests.auth import HTTPBasicAuth
import json

def get_component_attributes(nexus_url, username, password, repo, group=None, name=None, version=None):
    """
    Fetches the attributes of a component in a Nexus repository by Maven coordinates.
    
    Parameters:
      nexus_url : str
          Base URL of the Nexus instance (e.g., 'http://localhost:8081')
      username  : str
          Username for authentication.
      password  : str
          Password for authentication.
      repo      : str
          Name of the repository.
      group     : str, optional
          Maven group ID of the component.
      name      : str, optional
          Maven artifact name.
      version   : str, optional
          Maven artifact version.
    
    Returns:
      dict: Component details including its list of assets.
    
    Raises:
      Exception: If the component is not found or an error occurs.
    """
    auth = HTTPBasicAuth(username, password)
    # Build the initial URL with repository parameter and additional filters
    url = f"{nexus_url}/service/rest/v1/components?repository={repo}"
    if group:
        url += f"&group={group}"
    if name:
        url += f"&name={name}"
    if version:
        url += f"&version={version}"

    while True:
        response = requests.get(url, auth=auth)
        if response.status_code != 200:
            raise Exception(f"Error: {response.status_code} {response.text}")
        data = response.json()
        items = data.get('items', [])
        # Iterate over each component from this page
        for component in items:
            # If additional filters are provided, check them explicitly;
            # Though the API should return only matching components, this extra check is a safeguard.
            if group and component.get('group') != group:
                continue
            if name and component.get('name') != name:
                continue
            if version and component.get('version') != version:
                continue
            return component
        # Handle pagination
        continuationToken = data.get('continuationToken')
        if not continuationToken:
            break
        # Rebuild URL for the next page
        url = f"{nexus_url}/service/rest/v1/components?continuationToken={continuationToken}&repository={repo}"
        if group:
            url += f"&group={group}"
        if name:
            url += f"&name={name}"
        if version:
            url += f"&version={version}"
    
    raise Exception("Component not found")

# Example usage:
if __name__ == "__main__":
    nexus_url = "http://localhost:8081"
    username = "admin"
    password = "admin123"
    repo = "maven-central"
    # Set the Maven coordinates you want to filter by:
    group = "org.example"
    name = "my-library"
    version = "1.2.3"
    
    try:
        component = get_component_attributes(nexus_url, username, password, repo, group, name, version)
        print("Component found:")
        print(json.dumps(component, indent=2))
        
        # Optionally, iterate over the asset list within the component
        assets = component.get('assets', [])
        if assets:
            print("\nAssets:")
            for asset in assets:
                print(json.dumps(asset, indent=2))
        else:
            print("No assets found for this component.")
    except Exception as e:
        print(f"Error: {e}")
        
        
        
        
        
        
        
        
        ====
        
import requests
import json
from requests.auth import HTTPBasicAuth

# Nexus connection details
nexus_url = "http://localhost:8081"  # Replace with your Nexus instance URL
username = "admin"                   # Replace with your Nexus username
password = "admin123"               # Replace with your Nexus password

# Function to fetch components from Nexus with pagination
def fetch_nexus_components(repository, search_params=None):
    """
    Fetch components from Sonatype Nexus using REST API v1 with pagination.
    
    Args:
        repository (str): The name of the repository to search in
        search_params (dict): Optional dictionary of additional search parameters
        
    Returns:
        list: List of all component items fetched across all pages
    """
    # Base endpoint for component search
    endpoint = f"{nexus_url}/service/rest/v1/components"
    
    # Default parameters
    params = {
        "repository": repository
    }
    
    # Add additional search parameters if provided
    if search_params:
        params.update(search_params)
    
    all_items = []
    continuation_token = None
    
    try:
        while True:
            # Add continuation token to params if it exists
            if continuation_token:
                params["continuationToken"] = continuation_token
            
            # Make the GET request with basic authentication
            response = requests.get(
                endpoint,
                auth=HTTPBasicAuth(username, password),
                params=params,
                headers={"Accept": "application/json"},
                timeout=30
            )
            
            # Check if the request was successful
            response.raise_for_status()
            
            # Parse the JSON response
            result = response.json()
            
            # Add items to the collection
            if "items" in result and result["items"]:
                all_items.extend(result["items"])
                print(f"Fetched {len(result['items'])} items, total so far: {len(all_items)}")
            else:
                print("No more items found.")
                break
            
            # Check for continuation token
            continuation_token = result.get("continuationToken")
            if not continuation_token:
                print("No more pages to fetch.")
                break
                
        return all_items
        
    except requests.exceptions.HTTPError as http_err:
        print(f"HTTP error occurred: {http_err}")
        return None
    except requests.exceptions.RequestException as err:
        print(f"Error occurred: {err}")
        return None

# Example usage
def main():
    # Specify the repository
    target_repo = "docker-internal"
    
    # Define additional search parameters for Docker
    search_params = {
        "name": "my-app",         # Search for Docker image by name
        "version": "1.0.0"        # Search for specific version
    }
    
    # Fetch components
    components = fetch_nexus_components(target_repo, search_params)
    
    # Print the results
    if components:
        print(f"Total components found: {len(components)}")
        for item in components:
            print(json.dumps(item, indent=2))
    else:
        print("No components found or an error occurred.")

if __name__ == "__main__":
    main()
