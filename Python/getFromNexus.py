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
