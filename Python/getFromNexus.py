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