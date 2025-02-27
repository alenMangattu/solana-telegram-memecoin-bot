import matplotlib
matplotlib.use('Agg')  # Set non-interactive backend before importing pyplot
import os
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from matplotlib.ticker import MultipleLocator, FuncFormatter, MaxNLocator
from matplotlib.patches import Rectangle
import matplotlib.gridspec as gridspec
from io import BytesIO  # Import BytesIO

# Set default font size to avoid warnings
plt.rcParams['font.size'] = 10  # Set a default font size

# --- Configurable parameters ---
# Indicator settings
EMA_FAST_PERIOD = 14
EMA_SLOW_PERIOD = 50
RSI_PERIOD = 15

def calculate_rsi(series, period=14):
    delta = series.diff()
    gain = delta.where(delta > 0, 0)
    loss = -delta.where(delta < 0, 0)
    avg_gain = gain.ewm(com=period-1, adjust=False).mean()
    avg_loss = loss.ewm(com=period-1, adjust=False).mean()
    rs = avg_gain / avg_loss
    rsi = 100 - (100 / (1 + rs))
    return rsi 

def generate_chart(token_name, json_data, frequency, ema_fast_period=14, ema_slow_period=50, rsi_period=15):
    # --- Configurable parameters ---
    global EMA_FAST_PERIOD, EMA_SLOW_PERIOD, RSI_PERIOD
    EMA_FAST_PERIOD = ema_fast_period
    EMA_SLOW_PERIOD = ema_slow_period
    RSI_PERIOD = rsi_period

    # --- Parse JSON data into DataFrame ---
    items = json_data['data']['items']
    df = pd.DataFrame(items)
    df['datetime'] = pd.to_datetime(df['unixTime'], unit='s')

    # Check for duplicates
    duplicates = df[df.duplicated(['unixTime'], keep=False)]
    if not duplicates.empty:
        print("Duplicate entries found:")
        print(duplicates)

    # Remove duplicates
    df = df.drop_duplicates(subset=['unixTime'], keep='first')

    # Extract the token ticker
    token_ticker = json_data['data'].get('ticker', token_name)  # Use provided name if ticker not in data

    # --- Create OHLC data from the value field ---
    df = df.sort_values('unixTime')
    df['price_change'] = df['value'].diff()
    df['close'] = df['value']
    df['open'] = df['close'].shift(1)
    df.loc[df.index[0], 'open'] = df['close'].iloc[0]  # Set first open = first close

    # Estimate high and low with a small random variation
    np.random.seed(42)  # For reproducibility
    volatility = df['close'].mean() * 0.05  # 5% of average price for volatility
    df['high'] = df.apply(lambda row: max(row['open'], row['close']) + abs(np.random.normal(0, volatility)), axis=1)
    df['low'] = df.apply(lambda row: min(row['open'], row['close']) - abs(np.random.normal(0, volatility)), axis=1)

    # --- Calculate EMAs ---
    df[f'ema{EMA_FAST_PERIOD}'] = df['close'].ewm(span=EMA_FAST_PERIOD, adjust=False).mean()
    df[f'ema{EMA_SLOW_PERIOD}'] = df['close'].ewm(span=EMA_SLOW_PERIOD, adjust=False).mean()

    # --- Generate volume data based on price changes ---
    vol_base = 50000
    df['volume'] = vol_base + np.abs(df['price_change'] / df['close'].mean()) * vol_base * 10
    df['is_positive'] = df['price_change'] > 0

    # --- Calculate RSI ---
    df['rsi'] = calculate_rsi(df['close'], period=RSI_PERIOD)

    # --- Calculate header statistics ---
    last_price = df['close'].iloc[-1]
    first_price = df['close'].iloc[0]
    price_change = last_price - first_price
    price_change_pct = (price_change / first_price) * 100
    volume_value = df['volume'].iloc[-1]
    volume_change = df['volume'].iloc[-1] - df['volume'].iloc[-2]
    volume_change_pct = (volume_change / df['volume'].iloc[-2]) * 100
    price_color = '#26a69a' if price_change >= 0 else '#ef5350'
    volume_color = '#26a69a' if volume_change >= 0 else '#ef5350'
    price_change_sign = "+" if price_change >= 0 else "-"

    # --- Set up figure & axes ---
    plt.rcParams['font.family'] = 'DejaVu Sans'
    plt.rcParams['font.size'] = 8

    # Create figure with precise dimensions (1920x1080 at 160 DPI)
    fig = plt.figure(figsize=(12, 6.75), dpi=160, facecolor='white')  # 1920x1080 at 160 DPI
    
    # Create GridSpec with specific height ratios to match main.py
    gs = gridspec.GridSpec(2, 1, height_ratios=[7, 1.5], hspace=0)
    
    # Create axes
    price_ax = fig.add_subplot(gs[0])
    rsi_ax = fig.add_subplot(gs[1], sharex=price_ax)

    # Create an inset axis for volume inside the price panel with exact positioning
    vol_ax = price_ax.inset_axes([0, 0, 1, 0.25], transform=price_ax.transAxes)
    vol_ax.patch.set_visible(False)
    vol_ax.set_clip_on(False)
    vol_ax.set_zorder(price_ax.get_zorder()-1)
    vol_ax.tick_params(axis='y', which='both', left=False, labelleft=False)
    vol_ax.tick_params(axis='x', which='both', labelsize=0)
    for spine in vol_ax.spines.values():
        spine.set_visible(False)

    # --- Style tweaks: configure grid and axis styling ---
    grid_color = '#EEEEEE'
    axis_color = '#f3f3f4'  # Dark gray color for axes

    for ax in [price_ax, rsi_ax]:
        ax.set_facecolor('white')
        ax.grid(True, linestyle='-', linewidth=0.5, color=grid_color, zorder=-10)
        ax.spines['left'].set_visible(True)
        ax.spines['top'].set_visible(False)
        ax.spines['bottom'].set_visible(True)
        ax.spines['right'].set_visible(True)
        ax.spines['left'].set_color(axis_color)
        ax.spines['bottom'].set_color(axis_color)
        ax.spines['right'].set_color(axis_color)
        ax.yaxis.set_ticks_position("right")
        ax.yaxis.set_label_position("right")
        ax.tick_params(axis='y', labelright=True, labelleft=False, colors='black', labelsize=8, pad=5)

    # --- Add header text FIRST before plotting (to be under the chart elements) ---
    # --- Create TradingView style header ---
    # Title row with exchange and status dot
    exchange_name = "BIRDEYE, LUNAX TELEGRAM BOT"
    title_text = f"${token_ticker} / USD {frequency} {exchange_name}"
    title = price_ax.text(0.01, 0.99, title_text, transform=price_ax.transAxes,
                 fontsize=12, color='black', ha='left', va='top', zorder=15)
    
    # Create white background behind text to make it more visible
    # Add a white background rectangle behind the header text
    bbox = title.get_window_extent(fig.canvas.get_renderer()).transformed(price_ax.transAxes.inverted())
    rect = Rectangle((bbox.x0-0.005, bbox.y0-0.005), bbox.width+0.01, bbox.height+0.01, 
                    facecolor='white', alpha=0.7, transform=price_ax.transAxes, zorder=14)
    price_ax.add_patch(rect)
    
    # --- Price row with multiple values ---
    y_pos = 0.96  # Lowered slightly to avoid overlap
    x_start = 0.01

    # Current price (O H L C values)
    current_open = df['open'].iloc[-1]
    current_high = df['high'].iloc[-1]
    current_low = df['low'].iloc[-1]
    current_close = df['close'].iloc[-1]

    # Determine colors for each value based on their own direction
    open_color = '#26a69a' if current_open <= current_close else '#ef5350'
    high_color = '#26a69a'  # High is always positive
    low_color = '#ef5350'   # Low is always negative
    close_color = price_color

    # Make a list to collect all text objects for background
    text_objects = []
    background_patches = []
    
    # Current price (O H L C values) - Split label and value with different colors
    # Open
    x_start_change = 0.08
    y_pos_change = 0.03
    zorder = 15  # High z-order for text
    
    # Open
    open_label = price_ax.text(x_start, y_pos, "O", transform=price_ax.transAxes,
                 fontsize=9, color='black', ha='left', va='top', zorder=zorder)
    text_objects.append(open_label)
    
    open_value = price_ax.text(x_start + 0.01, y_pos, f"{current_open:.6f}", transform=price_ax.transAxes,
                 fontsize=9, color=open_color, ha='left', va='top', zorder=zorder)
    text_objects.append(open_value)
    x_start += x_start_change

    # High
    high_label = price_ax.text(x_start, y_pos, "H", transform=price_ax.transAxes,
                 fontsize=9, color='black', ha='left', va='top', zorder=zorder)
    text_objects.append(high_label)
    
    high_value = price_ax.text(x_start + 0.01, y_pos, f"{current_high:.6f}", transform=price_ax.transAxes,
                 fontsize=9, color=high_color, ha='left', va='top', zorder=zorder)
    text_objects.append(high_value)
    x_start += x_start_change

    # Low
    low_label = price_ax.text(x_start, y_pos, "L", transform=price_ax.transAxes,
                 fontsize=9, color='black', ha='left', va='top', zorder=zorder)
    text_objects.append(low_label)
    
    low_value = price_ax.text(x_start + 0.01, y_pos, f"{current_low:.6f}", transform=price_ax.transAxes,
                 fontsize=9, color=low_color, ha='left', va='top', zorder=zorder)
    text_objects.append(low_value)
    x_start += x_start_change

    # Close price (current price)
    close_label = price_ax.text(x_start, y_pos, "C", transform=price_ax.transAxes,
                 fontsize=9, color='black', ha='left', va='top', zorder=zorder)
    text_objects.append(close_label)
    
    close_value = price_ax.text(x_start + 0.01, y_pos, f"{current_close:.6f}", transform=price_ax.transAxes,
                 fontsize=9, color=close_color, ha='left', va='top', zorder=zorder)
    text_objects.append(close_value)
    x_start += x_start_change

    # Price change
    change_text = price_ax.text(x_start, y_pos, f"{price_change_sign}{abs(price_change):.6f} ({price_change_pct:+.2f}%)", 
                 transform=price_ax.transAxes, fontsize=9, color=price_color, ha='left', va='top', zorder=zorder)
    text_objects.append(change_text)

    # --- Indicator row ---
    # Increased spacing between rows
    y_pos -= y_pos_change
    x_start = 0.01

    # EMA
    indicator_name = "EMA" 
    ema_label = price_ax.text(x_start, y_pos, f"{indicator_name}", transform=price_ax.transAxes,
                 fontsize=9, color='black', ha='left', va='top', zorder=zorder)
    text_objects.append(ema_label)
    x_start += 0.05

    # Add indicator values with consistent spacing
    ema_fast_period = price_ax.text(x_start, y_pos, f"{EMA_FAST_PERIOD}", transform=price_ax.transAxes,
                 fontsize=9, color='gray', ha='left', va='top', zorder=zorder)
    text_objects.append(ema_fast_period)
    x_start += 0.02

    ema_fast_value = price_ax.text(x_start, y_pos, f"{df[f'ema{EMA_FAST_PERIOD}'].iloc[-1]:.6f}", transform=price_ax.transAxes,
                 fontsize=9, color='#2196F3', ha='left', va='top', zorder=zorder)
    text_objects.append(ema_fast_value)
    x_start += x_start_change

    ema_slow_period = price_ax.text(x_start, y_pos, f"{EMA_SLOW_PERIOD}", transform=price_ax.transAxes,
                 fontsize=9, color='gray', ha='left', va='top', zorder=zorder)
    text_objects.append(ema_slow_period)
    x_start += 0.02

    ema_slow_value = price_ax.text(x_start, y_pos, f"{df[f'ema{EMA_SLOW_PERIOD}'].iloc[-1]:.6f}", transform=price_ax.transAxes,
                 fontsize=9, color='#F44336', ha='left', va='top', zorder=zorder)
    text_objects.append(ema_slow_value)

    # --- Volume row ---
    y_pos -= y_pos_change
    volume_str = f"{df['volume'].iloc[-1]/1000:.2f}K"  # Format volume in K units for brevity

    # Split "Vol" (black) and the value (colored)
    vol_label = price_ax.text(0.01, y_pos, "Vol", transform=price_ax.transAxes,
                 fontsize=9, color='black', ha='left', va='top', zorder=zorder)
    text_objects.append(vol_label)
    
    vol_value = price_ax.text(0.035, y_pos, volume_str, transform=price_ax.transAxes,
                 fontsize=9, color=volume_color, ha='left', va='top', zorder=zorder)
    text_objects.append(vol_value)

    # Create white background for all text elements
    # fig.canvas.draw()  # This is needed to get the correct text dimensions
    # for text in text_objects:
    #     bbox = text.get_window_extent(fig.canvas.get_renderer()).transformed(price_ax.transAxes.inverted())
    #     rect = Rectangle((bbox.x0-0.005, bbox.y0-0.005), bbox.width+0.01, bbox.height+0.01, 
    #                     facecolor='white', alpha=0.7, transform=price_ax.transAxes, zorder=14)
    #     background_patches.append(rect)
    #     price_ax.add_patch(rect)

    # --- Plot VOLUME BARS in the inset ---
    max_volume = df['volume'].max()
    for i, (_, row) in enumerate(df.iterrows()):
        bar_color = '#26a69a' if row.get('is_positive', False) else '#ef5350'
        vol_ax.bar(i, row['volume'], width=0.8, color=bar_color, alpha=0.4,
                   clip_on=False, zorder=1)
    vol_ax.set_ylim(0, max_volume * 1.1)
    vol_ax.get_yaxis().set_visible(False)  # Hide volume y-axis completely

    # --- Plot CANDLESTICKS on the price axis ---
    candle_width = 0.6
    for i, (_, row) in enumerate(df.iterrows()):
        if row['close'] >= row['open']:
            color = '#26a69a'
            body_bottom = row['open']
            body_height = row['close'] - row['open']
            rect = Rectangle((i - candle_width / 2, body_bottom), candle_width, body_height,
                             facecolor=color, edgecolor=color, linewidth=0.5, zorder=2)
            price_ax.add_patch(rect)
            price_ax.plot([i, i], [row['low'], row['high']], color=color, linewidth=0.8, zorder=2)
        else:
            color = '#ef5350'
            body_bottom = row['close']
            body_height = row['open'] - row['close']
            rect = Rectangle((i - candle_width / 2, body_bottom), candle_width, body_height,
                             facecolor=color, edgecolor=color, linewidth=0.5, zorder=2)
            price_ax.add_patch(rect)
            price_ax.plot([i, i], [row['low'], row['high']], color=color, linewidth=0.8, zorder=2)

    # --- Plot EMAs on price_ax ---
    # Add an extra point at the end to ensure the lines touch the y-axis
    x_range = list(range(len(df))) + [len(df) - 0.01]  # Add a point very close to the last point
    ema_fast_extended = list(df[f'ema{EMA_FAST_PERIOD}']) + [df[f'ema{EMA_FAST_PERIOD}'].iloc[-1]]
    ema_slow_extended = list(df[f'ema{EMA_SLOW_PERIOD}']) + [df[f'ema{EMA_SLOW_PERIOD}'].iloc[-1]]

    # Plot with extended data to ensure lines touch the axis
    price_ax.plot(x_range, ema_fast_extended, color='#2196F3', linewidth=1.0, zorder=3)
    price_ax.plot(x_range, ema_slow_extended, color='#F44336', linewidth=1.0, zorder=3)

    # --- Also extend the RSI line to touch the y-axis ---
    rsi_extended = list(df['rsi']) + [df['rsi'].iloc[-1]]
    rsi_ax.plot(x_range, rsi_extended, color='#9C27B0', linewidth=1, zorder=2)

    # --- Plot RSI ---
    rsi_ax.axhline(y=70, color='#AAAAAA', linestyle='--', linewidth=0.5)
    rsi_ax.axhline(y=30, color='#AAAAAA', linestyle='--', linewidth=0.5)
    rsi_ax.set_ylim(0, 100)
    rsi_ax.set_facecolor('#F5F0FF')

    # --- Add a violet dotted line for the last RSI value ---
    last_rsi = df['rsi'].iloc[-1]  # Current RSI value
    rsi_ax.axhline(y=last_rsi, color='#9d15bf', linestyle=':', linewidth=1, alpha=0.7, zorder=3)

    # --- Fix RSI x-axis date labels ---
    rsi_ax.tick_params(axis='x', which='both', left=False, labelleft=False)
    rsi_ax.set_yticks([0, 30, 70, 100])

    # --- Calculate price_min and price_max ---
    price_min = df['low'].min()
    price_max = df['high'].max()
    price_range = price_max - price_min

    # Ensure we don't have a zero price range
    if price_range < 0.0000001:  # Prevent division by zero with small values
        price_range = 0.0000001

    # --- Format y-axis labels for price ---
    def price_formatter(x, pos):
        return f'{x:.6f}'  # Format to 6 decimal places for small values
    price_ax.yaxis.set_major_formatter(FuncFormatter(price_formatter))
    vol_ax.ticklabel_format(style='plain')

    # For x-axis grid lines
    x_ticks = 8  # Desired number of x ticks
    x_interval = max(1, len(df) // x_ticks)
    price_ax.xaxis.set_major_locator(MultipleLocator(x_interval))

    # For y-axis, use a standard locator to ensure price labels always appear
    price_ax.yaxis.set_major_locator(MaxNLocator(nbins=6, prune='both'))  # Ensure no duplicates

    # --- Add last price line (horizontal dotted line at last price) ---
    last_price = df['close'].iloc[-1]
    price_ax.axhline(y=last_price, color=price_color, linestyle=':', linewidth=1, alpha=0.7, zorder=4)

    # Set y-axis limits to ensure visibility
    padding = price_range * 0.05  # 5% padding
    price_ax.set_ylim(price_min - padding, price_max + padding)

    # --- Add price notification labels on y-axis with improved alignment and anti-overlap ---
    price_labels = []

    # Format price with 6 decimal places
    price_str = f'{last_price:.6f}'
    
    # Get last EMAs for labels
    last_ema_fast = df[f'ema{EMA_FAST_PERIOD}'].iloc[-1]
    last_ema_slow = df[f'ema{EMA_SLOW_PERIOD}'].iloc[-1]
    ema_fast_str = f'{last_ema_fast:.6f}'
    ema_slow_str = f'{last_ema_slow:.6f}'

    def add_label(ax, y, text, color, fontsize=7, zorder=15):
        """Function to add a label to the specified axis that's attached to the y-axis."""
        return ax.text(
            1.0,                        # Position exactly at the edge
            y,                          # Y position
            f' {text} ',                # Add space to prevent text from touching edge
            fontsize=fontsize,          # Font size
            color='white',              # Text color
            ha='left',                  # Left-aligned to attach to y-axis
            va='center',                # Vertically centered
            transform=ax.transAxes,     # Use axes coordinates
            bbox=dict(
                facecolor=color,        # Background color
                alpha=1.0,              # Fully opaque
                boxstyle='round',       # Rounded corners
                pad=0.2,                # Padding
                edgecolor='none'        # No edge color
            ),
            zorder=15                   # Very high zorder to ensure visibility
        )

    # When calculating y-positions for labels, ensure we include padding in calculations
    padding = price_range * 0.05  # 5% padding
    actual_ymin = price_min - padding
    actual_ymax = price_max + padding

    # Use direct data coordinates for price labels
    def add_price_label(ax, value, text, color, fontsize=7, zorder=15):
        """Add a label using data coordinates for more precise positioning"""
        return ax.annotate(
            f' {text} ', 
            xy=(len(df) - 0.5, value),  # Position at the right edge of data
            xycoords='data',            # Use data coordinates
            fontsize=fontsize,
            color='white',
            ha='left',
            va='center',
            bbox=dict(
                facecolor=color,
                alpha=1.0,
                boxstyle='round',
                pad=0.2,
                edgecolor='none'
            ),
            zorder=15
        )

    # Create array of values and sort them
    values = [last_price, last_ema_fast, last_ema_slow]
    colors = [price_color, '#2196F3', '#F44336']  
    texts = [price_str, ema_fast_str, ema_slow_str]

    # First sort values from low to high
    pairs = sorted(zip(values, colors, texts))
    sorted_values = [p[0] for p in pairs]
    sorted_colors = [p[1] for p in pairs]
    sorted_texts = [p[2] for p in pairs]

    # Apply spacing algorithm to prevent overlap in data coordinates
    min_gap_data = price_range * 0.05  # 5% gap in actual price units
    for i in range(1, len(sorted_values)):
        if sorted_values[i] - sorted_values[i-1] < min_gap_data:
            sorted_values[i] = sorted_values[i-1] + min_gap_data
            # If we've pushed it too high, pull everything down
            if sorted_values[i] > actual_ymax:
                offset = sorted_values[i] - actual_ymax
                for j in range(i, -1, -1):
                    sorted_values[j] -= offset
                    if j > 0 and sorted_values[j] - sorted_values[j-1] < min_gap_data:
                        break

    # Create price labels at adjusted positions
    for i in range(len(sorted_values)):
        label = add_price_label(price_ax, sorted_values[i], sorted_texts[i], sorted_colors[i])
        price_labels.append(label)

    # Add RSI label separately (using the working method for RSI)
    rsi_str = f'{last_rsi:.1f}'
    rsi_rel = last_rsi / 100.0  # Scale RSI 0-100 to 0-1
    rsi_label = add_label(rsi_ax, rsi_rel, rsi_str, '#9C27B0')
    price_labels.append(rsi_label)

    # --- Dynamic x-axis formatting with non-overlapping labels (TradingView style) ---
    time_span = df['datetime'].iloc[-1] - df['datetime'].iloc[0]
    if time_span < timedelta(days=2):
        date_format = '%H:%M'
    elif time_span < timedelta(days=7):
        date_format = '%d %b %H:%M'
    elif time_span < timedelta(days=180):
        date_format = '%d %b'
    else:
        date_format = '%b %Y'

    def date_formatter(x, pos):
        if x < 0 or x >= len(df):
            return ''
        date = df['datetime'].iloc[int(x)]
        return date.strftime(date_format)

    # Use MaxNLocator to auto-select a reasonable number of ticks without overlapping
    total_points = len(df)
    ideal_ticks = min(total_points, max(5, int(fig.get_figwidth() * 1.5)))

    # Use MaxNLocator for better x-axis label distribution
    rsi_ax.xaxis.set_major_locator(MaxNLocator(nbins=ideal_ticks, integer=True, prune='both'))
    rsi_ax.xaxis.set_major_formatter(FuncFormatter(date_formatter))
    rsi_ax.tick_params(axis='x', labelsize=8, rotation=0)  # Set rotation to 0 for horizontal text

    # Ensure the x-axis has correct limits with NO extra space
    price_ax.set_xlim(-0.5, len(df) - 0.5)  # Exact limit
    vol_ax.set_xlim(-0.5, len(df) - 0.5)    # Match the price axis

    # Adjust subplot margins to make right y-axis visible with labels
    plt.subplots_adjust(left=0, right=0.92, top=0.99, bottom=0.05)

    # Add watermark with improved styling
    fig.text(0.5, 0.5, 'LUNAX TELEGRAM BOT', 
             fontsize=50, color='blue', ha='center', va='center', alpha=0.06,
             rotation=30, transform=fig.transFigure, zorder=5)

    # Ensure x-axis labels only appear at the bottom
    price_ax.tick_params(axis='x', which='both', labelbottom=False)  # No labels on price chart
    rsi_ax.tick_params(axis='x', which='both', labelbottom=True)     # Only show labels on RSI chart

    # --- Save to in-memory buffer instead of a file ---
    buf = BytesIO()
    plt.savefig(buf, format='png', dpi=160, bbox_inches='tight', pad_inches=0.1)
    buf.seek(0)  # Rewind the buffer to the beginning

    # Close the figure to free up memory
    plt.close(fig)

    return buf  # Return the buffer instead of a filename 