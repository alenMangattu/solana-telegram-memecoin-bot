import requests
import json

def fetch_data(url, headers=None):
    """
    Fetch data from an API endpoint.
    
    Args:
        url (str): The URL to fetch data from
        headers (dict): Optional headers to include in the request
        
    Returns:
        dict: JSON response
    """
    response = requests.get(url, headers=headers)
    response.raise_for_status()  # Raise an error for bad responses
    return response.json()

def fetch_historical_price(token_address, frequency, time_from, time_to):
    """
    Fetch historical price data from BirdEye API.
    
    Args:
        token_address (str): The token address
        frequency (str): Time interval (e.g., '15m', '1h', '1d')
        time_from (int): Start timestamp
        time_to (int): End timestamp
        
    Returns:
        dict: JSON response with historical price data
    """
    url = f"https://public-api.birdeye.so/defi/history_price?address={token_address}&address_type=token&type={frequency}&time_from={time_from}&time_to={time_to}"
    custom_headers = {
        "x-chain": "solana",
        "x-api-key": "781b4ec927674ed09548a0a8fcd39ed3"
    }
    return fetch_data(url, headers=custom_headers) 