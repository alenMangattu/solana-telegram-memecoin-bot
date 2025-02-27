# import os
# import matplotlib.pyplot as plt
# import pandas as pd
# import numpy as np
# import json
# from datetime import datetime, timedelta
# from matplotlib.ticker import MultipleLocator, FuncFormatter, MaxNLocator
# from matplotlib.patches import Rectangle
# import matplotlib.gridspec as gridspec
# import requests

# # --- Configurable parameters ---
# # Indicator settings
# EMA_FAST_PERIOD = 14
# EMA_SLOW_PERIOD = 50
# RSI_PERIOD = 15

# # --- Function to fetch data from the API ---
# def fetch_data(url, headers=None):
#     response = requests.get(url, headers=headers)
#     response.raise_for_status()  # Raise an error for bad responses
#     return response.json()

# # --- New function to fetch historical price data ---
# def fetch_historical_price(token_address, frequency, time_from, time_to):
#     url = f"https://public-api.birdeye.so/defi/history_price?address={token_address}&address_type=token&type={frequency}&time_from={time_from}&time_to={time_to}"
#     custom_headers = {
#         "x-chain": "solana",
#         "x-api-key": "781b4ec927674ed09548a0a8fcd39ed3"
#     }
#     return fetch_data(url, headers=custom_headers)

# # --- Function to calculate RSI ---
# def calculate_rsi(series, period=14):
#     delta = series.diff()
#     gain = delta.where(delta > 0, 0)
#     loss = -delta.where(delta < 0, 0)
#     avg_gain = gain.ewm(com=period-1, adjust=False).mean()
#     avg_loss = loss.ewm(com=period-1, adjust=False).mean()
#     rs = avg_gain / avg_loss
#     rsi = 100 - (100 / (1 + rs))
#     return rsi

# # --- Function to generate the chart ---
# def generate_chart(token_name, json_data, frequency, ema_fast_period=14, ema_slow_period=50, rsi_period=15):
#     global EMA_FAST_PERIOD, EMA_SLOW_PERIOD, RSI_PERIOD
#     EMA_FAST_PERIOD = ema_fast_period
#     EMA_SLOW_PERIOD = ema_slow_period
#     RSI_PERIOD = rsi_period

#     # --- Parse JSON data into DataFrame ---
#     items = json_data['data']['items']
#     df = pd.DataFrame(items)
#     df['datetime'] = pd.to_datetime(df['unixTime'], unit='s')

#     # Check for duplicates
#     duplicates = df[df.duplicated(['unixTime'], keep=False)]
#     if not duplicates.empty:
#         print("Duplicate entries found:")
#         print(duplicates)

#     # Remove duplicates
#     df = df.drop_duplicates(subset=['unixTime'], keep='first')

#     # Extract the token ticker
#     token_ticker = json_data['data'].get('ticker', 'Unknown')  # Default to 'Unknown' if not in response

#     # --- Create OHLC data from the value field ---
#     df = df.sort_values('unixTime')
#     df['price_change'] = df['value'].diff()
#     df['close'] = df['value']
#     df['open'] = df['close'].shift(1)
#     df.loc[df.index[0], 'open'] = df['close'].iloc[0]  # Set first open = first close

#     # Estimate high and low with a small random variation
#     np.random.seed(42)  # For reproducibility
#     volatility = df['close'].mean() * 0.05  # 5% of average price for volatility
#     df['high'] = df.apply(lambda row: max(row['open'], row['close']) + abs(np.random.normal(0, volatility)), axis=1)
#     df['low'] = df.apply(lambda row: min(row['open'], row['close']) - abs(np.random.normal(0, volatility)), axis=1)

#     # --- Calculate EMAs ---
#     df[f'ema{EMA_FAST_PERIOD}'] = df['close'].ewm(span=EMA_FAST_PERIOD, adjust=False).mean()
#     df[f'ema{EMA_SLOW_PERIOD}'] = df['close'].ewm(span=EMA_SLOW_PERIOD, adjust=False).mean()

#     # --- Generate volume data based on price changes ---
#     vol_base = 50000
#     df['volume'] = vol_base + np.abs(df['price_change'] / df['close'].mean()) * vol_base * 10
#     df['is_positive'] = df['price_change'] > 0

#     # --- Calculate RSI ---
#     df['rsi'] = calculate_rsi(df['close'], period=RSI_PERIOD)

# # --- Calculate header statistics ---
#     last_price = df['close'].iloc[-1]
#     first_price = df['close'].iloc[0]
# price_change = last_price - first_price
# price_change_pct = (price_change / first_price) * 100
#     volume_value = df['volume'].iloc[-1]
#     volume_change = df['volume'].iloc[-1] - df['volume'].iloc[-2]
#     volume_change_pct = (volume_change / df['volume'].iloc[-2]) * 100
# price_color = '#26a69a' if price_change >= 0 else '#ef5350'
# volume_color = '#26a69a' if volume_change >= 0 else '#ef5350'
# price_change_sign = "+" if price_change >= 0 else "-"

# # --- Set up figure & axes ---
# plt.rcParams['font.family'] = 'DejaVu Sans'
# plt.rcParams['font.size'] = 8

# fig = plt.figure(figsize=(12, 7), facecolor='white')
# gs = gridspec.GridSpec(2, 1, height_ratios=[7, 1.5], hspace=0)
# price_ax = fig.add_subplot(gs[0])
# rsi_ax = fig.add_subplot(gs[1], sharex=price_ax)

# # Create an inset axis for volume inside the price panel.
# vol_ax = price_ax.inset_axes([0, 0, 1, 0.25], transform=price_ax.transAxes)
# vol_ax.patch.set_visible(False)
# vol_ax.set_clip_on(False)
#     vol_ax.set_zorder(price_ax.get_zorder() - 1)
# vol_ax.tick_params(axis='y', which='both', left=False, labelleft=False)
# vol_ax.tick_params(axis='x', which='both', labelsize=0)
# for spine in vol_ax.spines.values():
#     spine.set_visible(False)

# # --- Style tweaks: configure grid and axis styling ---
# grid_color = '#EEEEEE'
#     axis_color = '#f3f3f4'  # Dark gray color for axes

# for ax in [price_ax, rsi_ax]:
#     ax.set_facecolor('white')
#         ax.grid(True, linestyle='-', linewidth=0.5, color=grid_color, zorder=-10)
#         ax.spines['left'].set_visible(True)
#     ax.spines['top'].set_visible(False)
#         ax.spines['bottom'].set_visible(True)
#     ax.spines['right'].set_visible(True)
#         ax.spines['left'].set_color(axis_color)
#         ax.spines['bottom'].set_color(axis_color)
#     ax.spines['right'].set_color(axis_color)
#     ax.yaxis.set_ticks_position("right")
#     ax.yaxis.set_label_position("right")
#     ax.tick_params(axis='y', labelright=True, labelleft=False, colors='black', labelsize=8, pad=5)

# # --- Plot VOLUME BARS in the inset ---
#     max_volume = df['volume'].max()
#     for i, (_, row) in enumerate(df.iterrows()):
#         bar_color = '#26a69a' if row.get('is_positive', False) else '#ef5350'
#     vol_ax.bar(i, row['volume'], width=0.8, color=bar_color, alpha=0.4,
#                clip_on=False, zorder=1)
# vol_ax.set_ylim(0, max_volume * 1.1)
# vol_ax.get_yaxis().set_visible(False)  # Hide volume y-axis completely

# # --- Plot CANDLESTICKS on the price axis ---
# candle_width = 0.6
#     for i, (_, row) in enumerate(df.iterrows()):
#     if row['close'] >= row['open']:
#         color = '#26a69a'
#         body_bottom = row['open']
#         body_height = row['close'] - row['open']
#     else:
#         color = '#ef5350'
#         body_bottom = row['close']
#         body_height = row['open'] - row['close']
#         rect = Rectangle((i - candle_width / 2, body_bottom), candle_width, body_height,
#                      facecolor=color, edgecolor=color, linewidth=0.5, zorder=2)
#     price_ax.add_patch(rect)
#     price_ax.plot([i, i], [row['low'], row['high']], color=color, linewidth=0.8, zorder=2)

# # --- Plot EMAs on price_ax ---
#     price_ax.plot(range(len(df)), df[f'ema{EMA_FAST_PERIOD}'], color='#2196F3', linewidth=1.0, zorder=3)
#     price_ax.plot(range(len(df)), df[f'ema{EMA_SLOW_PERIOD}'], color='#F44336', linewidth=1.0, zorder=3)

# # --- Plot RSI ---
#     rsi_ax.plot(range(len(df)), df['rsi'], color='#9C27B0', linewidth=1, zorder=2)
# rsi_ax.axhline(y=70, color='#AAAAAA', linestyle='--', linewidth=0.5)
# rsi_ax.axhline(y=30, color='#AAAAAA', linestyle='--', linewidth=0.5)
# rsi_ax.set_ylim(0, 100)
# rsi_ax.set_facecolor('#F5F0FF')

#     # --- Add a violet dotted line for the last RSI value ---
#     last_rsi = df['rsi'].iloc[-1]  # Current RSI value
#     rsi_ax.axhline(y=last_rsi, color='#9d15bf', linestyle=':', linewidth=1, alpha=0.7, zorder=3)

#     # --- Fix RSI x-axis date labels ---
#     rsi_ax.tick_params(axis='x', which='both', left=False, labelleft=False)
#     rsi_ax.set_yticks([0, 30, 70, 100])

# # --- Format y-axis labels for price ---
# def price_formatter(x, pos):
#         return f'{x:.6f}'  # Format to 6 decimal places for small values
# price_ax.yaxis.set_major_formatter(FuncFormatter(price_formatter))
# vol_ax.ticklabel_format(style='plain')

#     # --- Create square grid ---
#     price_min = df['low'].min()
#     price_max = df['high'].max()
#     price_range = price_max - price_min

#     # Ensure we don't have a zero price range
#     if price_range < 0.0000001:  # Prevent division by zero with small values
#         price_range = 0.0000001

#     # For x-axis grid lines
#     x_ticks = 8  # Desired number of x ticks
#     x_interval = max(1, len(df) // x_ticks)
#     price_ax.xaxis.set_major_locator(MultipleLocator(x_interval))

#     # For y-axis, use a standard locator to ensure price labels always appear
#     price_ax.yaxis.set_major_locator(MaxNLocator(nbins=6, prune='both'))  # Ensure no duplicates

#     # --- Add last price line (horizontal dotted line at last price) ---
#     last_price = df['close'].iloc[-1]
#     price_ax.axhline(y=last_price, color=price_color, linestyle=':', linewidth=1, alpha=0.7, zorder=4)

#     # --- Set y-axis limits to ensure visibility ---
#     padding = price_range * 0.05  # 5% padding
#     price_ax.set_ylim(price_min - padding, price_max + padding)

#     # --- Add price notification labels on y-axis with overlap prevention ---
#     price_labels = []

#     # Format price with 6 decimal places
#     price_str = f'{last_price:.6f}'

#     def add_label(ax, x, y, text, color, fontsize=7):
#         """Function to add a label to the specified axis."""
#         return ax.text(
#             x,                          # X position
#             y,                          # Y position
#             f'{text}',                 # Text to display
#             fontsize=fontsize,         # Font size
#             color='white',             # Text color
#             ha='center',               # Center-aligned
#             va='center',               # Vertically centered
#             bbox=dict(
#                 facecolor=color,       # Background color
#                 alpha=0.9,            # Almost opaque
#                 boxstyle='round',     # Rounded corners
#                 pad=0.2,              # Padding
#                 edgecolor='none'      # No edge color
#             ),
#             zorder=100                 # Very high zorder to ensure visibility
#         )

#     # Get all the values we need to label
#     last_ema_fast = df[f'ema{EMA_FAST_PERIOD}'].iloc[-1]
#     last_ema_slow = df[f'ema{EMA_SLOW_PERIOD}'].iloc[-1]
#     ema_fast_str = f'{last_ema_fast:.6f}'
#     ema_slow_str = f'{last_ema_slow:.6f}'

#     # Calculate price range for scaling adjustments
# price_range = price_max - price_min
#     min_gap = price_range * 0.03  # Minimum 3% of price range as gap

#     # Create array of values and sort them
#     values = [last_price, last_ema_fast, last_ema_slow]
#     colors = [price_color, '#2196F3', '#F44336']  
#     texts = [price_str, ema_fast_str, ema_slow_str]
#     positions = []

#     # Sort by value for easier adjustment
#     indices = sorted(range(len(values)), key=lambda i: values[i])
#     sorted_values = [values[i] for i in indices]
#     sorted_colors = [colors[i] for i in indices]
#     sorted_texts = [texts[i] for i in indices]

#     # Calculate adjusted positions
#     adjusted_positions = sorted_values.copy()
#     for i in range(1, len(adjusted_positions)):
#         if adjusted_positions[i] - adjusted_positions[i-1] < min_gap:
#             adjusted_positions[i] = adjusted_positions[i-1] + min_gap

#     # Restore original order but keep adjusted positions
#     for i, idx in enumerate(indices):
#         positions.append(adjusted_positions[i])

#     # Now create labels with adjusted positions
#     for i, idx in enumerate(indices):
#         label = add_label(price_ax, len(df) + 1.8, positions[i], sorted_texts[i], sorted_colors[i])
#         price_labels.append(label)

#     # Add RSI label separately (it's on a different axis)
#     rsi_str = f'{last_rsi:.1f}'
#     rsi_label = add_label(rsi_ax, len(df) + 0.7, last_rsi, rsi_str, '#9C27B0')
#     price_labels.append(rsi_label)

#     # --- Move this BEFORE we create any header text ---
#     # Clear ALL existing header text
#     for text in price_ax.texts[:]:
#         if text not in price_labels:  # Skip price labels
#     text.remove()

#     # --- Create TradingView style header ---
#     # Title row with exchange and status dot
#     exchange_name = "BIRDEYE, LUNAX TELEGRAM BOT"
#     title_text = f"{token_ticker} / USD {frequency} {exchange_name}"
#     price_ax.text(0.01, 0.99, title_text, transform=price_ax.transAxes,
#                   fontsize=12, color='black', ha='left', va='top')

#     # --- Price row with multiple values ---
#     y_pos = 0.94  # Lowered slightly to avoid overlap
#     x_start = 0.01

#     # Current price (O H L C values)
#     current_open = df['open'].iloc[-1]
#     current_high = df['high'].iloc[-1]
#     current_low = df['low'].iloc[-1]
#     current_close = df['close'].iloc[-1]

#     # Determine colors for each value based on their own direction
#     open_color = '#26a69a' if current_open <= current_close else '#ef5350'
#     high_color = '#26a69a'  # High is always positive
#     low_color = '#ef5350'   # Low is always negative
#     close_color = price_color

#     # Current price (O H L C values) - Split label and value with different colors
#     # Open
#     x_start_change = 0.08
#     y_pos_change = 0.03
#     price_ax.text(x_start, y_pos, "O", transform=price_ax.transAxes,
#                   fontsize=9, color='black', ha='left', va='top')
#     price_ax.text(x_start + 0.01, y_pos, f"{current_open:.6f}", transform=price_ax.transAxes,
#                   fontsize=9, color=open_color, ha='left', va='top')
#     x_start += x_start_change  # Increased spacing further

#     # High
#     price_ax.text(x_start, y_pos, "H", transform=price_ax.transAxes,
#                   fontsize=9, color='black', ha='left', va='top')
#     price_ax.text(x_start + 0.01, y_pos, f"{current_high:.6f}", transform=price_ax.transAxes,
#                   fontsize=9, color=high_color, ha='left', va='top')
#     x_start += x_start_change  # Increased spacing further

#     # Low
#     price_ax.text(x_start, y_pos, "L", transform=price_ax.transAxes,
#                   fontsize=9, color='black', ha='left', va='top')
#     price_ax.text(x_start + 0.01, y_pos, f"{current_low:.6f}", transform=price_ax.transAxes,
#                   fontsize=9, color=low_color, ha='left', va='top')
#     x_start += x_start_change  # Increased spacing further

#     # Close price (current price)
#     price_ax.text(x_start, y_pos, "C", transform=price_ax.transAxes,
#                   fontsize=9, color='black', ha='left', va='top')
#     price_ax.text(x_start + 0.01, y_pos, f"{current_close:.6f}", transform=price_ax.transAxes,
#                   fontsize=9, color=close_color, ha='left', va='top')
#     x_start += x_start_change  # Increased spacing further

#     # Price change
#     price_ax.text(x_start, y_pos, f"{price_change_sign}{abs(price_change):.6f} ({price_change_pct:+.2f}%)", 
#                   transform=price_ax.transAxes, fontsize=9, color=price_color, ha='left', va='top')

#     # --- Indicator row ---
#     # Increased spacing between rows
#     y_pos -= y_pos_change
#     x_start = 0.01

#     indicator_name = "EMA" 
#     price_ax.text(x_start, y_pos, f"{indicator_name}", transform=price_ax.transAxes,
#                   fontsize=9, color='black', ha='left', va='top')
#     x_start += 0.05  # Increased spacing

#     # Add indicator values with consistent spacing
#     price_ax.text(x_start, y_pos, f"{EMA_FAST_PERIOD}", transform=price_ax.transAxes,
#                   fontsize=9, color='gray', ha='left', va='top')
#     x_start += 0.02

#     price_ax.text(x_start, y_pos, f"{ema_fast_str}", transform=price_ax.transAxes,
#                   fontsize=9, color='#2196F3', ha='left', va='top')
#     x_start += x_start_change  # Increased spacing

#     price_ax.text(x_start, y_pos, f"{EMA_SLOW_PERIOD}", transform=price_ax.transAxes,
#                   fontsize=9, color='gray', ha='left', va='top')
#     x_start += 0.02

#     price_ax.text(x_start, y_pos, f"{ema_slow_str}", transform=price_ax.transAxes,
#                   fontsize=9, color='#F44336', ha='left', va='top')

#     # --- Volume row ---
#     y_pos -= y_pos_change  # Increased spacing between rows
#     volume_str = f"{volume_value/1000:.2f}K"  # Format volume in K units for brevity

#     # Split "Vol" (black) and the value (colored)
#     price_ax.text(0.01, y_pos, "Vol", transform=price_ax.transAxes,
#                   fontsize=9, color='black', ha='left', va='top')
#     price_ax.text(0.035, y_pos, volume_str, transform=price_ax.transAxes,
#                   fontsize=9, color=volume_color, ha='left', va='top')

#     # --- Dynamic x-axis formatting with non-overlapping labels (TradingView style) ---
#     time_span = df['datetime'].iloc[-1] - df['datetime'].iloc[0]
# if time_span < timedelta(days=2):
#         date_format = '%H:%M'
# elif time_span < timedelta(days=7):
#         date_format = '%d %b %H:%M'
#     elif time_span < timedelta(days=180):
#         date_format = '%d %b'
# else:
#         date_format = '%b %Y'

# def date_formatter(x, pos):
#         if x < 0 or x >= len(df):
#         return ''
#         date = df['datetime'].iloc[int(x)]
#     return date.strftime(date_format)

#     # Use MaxNLocator to auto-select a reasonable number of ticks without overlapping
#     total_points = len(df)
#     ideal_ticks = min(total_points, max(5, int(fig.get_figwidth() * 1.5)))

#     # Use MaxNLocator for better x-axis label distribution
#     rsi_ax.xaxis.set_major_locator(MaxNLocator(nbins=ideal_ticks, integer=True, prune='both'))
# rsi_ax.xaxis.set_major_formatter(FuncFormatter(date_formatter))
#     rsi_ax.tick_params(axis='x', labelsize=8, rotation=0)  # Set rotation to 0 for horizontal text

# # Ensure the x-axis starts exactly at the first candle
#     price_ax.set_xlim(-0.5, len(df) - 0.5)
#     vol_ax.set_xlim(-0.5, len(df) - 0.5)

#     # Adjust subplot margins to reduce white space
#     plt.subplots_adjust(left=0, right=0.94, top=1, bottom=0.05)

#     # Add watermark with improved styling
#     fig.text(0.5, 0.5, 'LUNAX TELEGRAM BOT', 
#              fontsize=50, color='blue', ha='center', va='center', alpha=0.06,
#              rotation=30, transform=fig.transFigure)

#     # Ensure x-axis labels only appear at the bottom
#     price_ax.tick_params(axis='x', which='both', labelbottom=False)  # No labels on price chart
#     rsi_ax.tick_params(axis='x', which='both', labelbottom=True)     # Only show labels on RSI chart

#     # Save to high-resolution PNG file
#     output_dir = "charts"
#     os.makedirs(output_dir, exist_ok=True)
#     timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
#     filename = f"{output_dir}/{token_ticker}_{frequency}_{timestamp}.png"

#     # Set DPI for high resolution (1920x1080)
#     dpi = 160

#     # Save the figure
#     plt.savefig(filename, dpi=dpi, bbox_inches='tight', pad_inches=0.1)
#     print(f"Chart saved to {filename}")

#     # Close the figure to free up memory
#     plt.close(fig)
    
#     return filename

# # Example usage
# if __name__ == "__main__":
#     token_address = "2eu1K3wvfPC7gVj1CK8ohv4ggusdN6qxyxpjHyTCkjZT"
#     frequency = "15m"
#     time_from = 1740519868
#     time_to = 1740609897
#     token_ticker = "SOMETGHINGIDK"

#     # Example usage:
#     json_data = fetch_historical_price(token_address, frequency, time_from, time_to)
#     generate_chart(token_ticker, json_data, frequency)
