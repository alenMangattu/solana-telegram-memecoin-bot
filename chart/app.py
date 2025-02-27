from flask import Flask, request, send_file, jsonify
import os
from chart_generator import generate_chart
from utils import fetch_historical_price
from io import BytesIO
import logging
from flask_compress import Compress
from flask_caching import Cache

# Configure logging
logging.basicConfig(level=logging.INFO, 
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
Compress(app)

cache = Cache(config={'CACHE_TYPE': 'SimpleCache', 
                      'CACHE_DEFAULT_TIMEOUT': 300})
cache.init_app(app)

@app.route('/')
def index():
    return "Hello, World!"

@app.route('/chart', methods=['POST'])
def create_chart():
    try:
        # Get request data
        data = request.json
        logger.info(f"Received data: {data}")
        if not data:
            return jsonify({"error": "No data provided"}), 400
        
        # Extract parameters from request
        token_ticker = data.get('token_ticker')
        frequency = data.get('frequency')
        ema_fast_period = data.get('ema_fast_period', 14)
        ema_slow_period = data.get('ema_slow_period', 50)
        rsi_period = data.get('rsi_period', 15)
        
        # Check if price data is included directly in the request
        json_data = None
        if 'data' in data and 'items' in data['data']:
            # Use the provided data
            json_data = {
                'data': data['data']
            }
            if not token_ticker:
                return jsonify({"error": "token_ticker is required"}), 400
            if not frequency:
                return jsonify({"error": "frequency is required"}), 400
        else:
            # Fetch data from Birdeye API
            token_address = data.get('token_address')
            time_from = data.get('time_from')
            time_to = data.get('time_to')
            
            # Validate required parameters for API fetch
            if not all([token_address, token_ticker, frequency, time_from, time_to]):
                return jsonify({"error": "Missing required parameters for API fetch"}), 400
                
            # Fetch price data
            try:
                json_data = fetch_historical_price(token_address, frequency, time_from, time_to)
            except Exception as e:
                logger.error(f"Failed to fetch price data: {str(e)}")
                return jsonify({"error": f"Failed to fetch price data: {str(e)}"}), 500
        
        # Ensure we have valid data
        if not json_data or 'data' not in json_data or 'items' not in json_data['data'] or not json_data['data']['items']:
            return jsonify({"error": "No valid price data available"}), 400
            
        # Generate chart
        chart_buffer = generate_chart(token_ticker, json_data, frequency, ema_fast_period, ema_slow_period, rsi_period)
        
        # Send file to client
        return send_file(chart_buffer, mimetype='image/png')
        
    except Exception as e:
        logger.error(f"Error generating chart: {str(e)}")
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    # Using production server with waitress
    from waitress import serve
    # Increase number of threads to handle more concurrent requests
    serve(app, host='0.0.0.0', port=7000, threads=16)
    # app.run(host='0.0.0.0', port=7000, debug=True) 