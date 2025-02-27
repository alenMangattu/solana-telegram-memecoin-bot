# Gunicorn configuration for high performance
import multiprocessing

# Number of worker processes
workers = multiprocessing.cpu_count() * 2 + 1

# Number of threads per worker
threads = 4

# Maximum number of simultaneous clients
max_requests = 1000
max_requests_jitter = 50

# Keep-alive timeout
keepalive = 5

# Server socket timeout
timeout = 30

# Log level
loglevel = 'info'

# Preload application code
preload_app = True 