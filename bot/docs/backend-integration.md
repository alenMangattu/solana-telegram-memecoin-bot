# Backend Integration Documentation

## Overview
The LunaX Telegram Bot integrates with a backend service running on `localhost:3000`. This document describes the integration points, communication patterns, and data flow between the bot and the backend service.

## Communication Protocol
The bot communicates with the backend service using HTTP/HTTPS requests. All requests are made to the base URL: `http://localhost:3000`

## API Endpoints

### Authentication
- **POST** `/api/auth/login`
  - Handles user authentication
  - Required headers: `Content-Type: application/json`
  - Request body: `{ "username": "string", "password": "string" }`
  - Response: JWT token for subsequent requests

### User Management
- **GET** `/api/users/me`
  - Retrieves current user information
  - Required headers: `Authorization: Bearer <token>`
  - Response: User profile data

### Chat State Management
- **POST** `/api/chat/state`
  - Updates chat state
  - Required headers: `Authorization: Bearer <token>`
  - Request body: `{ "chatId": "string", "state": "string", "data": {} }`

### Message Processing
- **POST** `/api/messages/process`
  - Processes incoming messages
  - Required headers: `Authorization: Bearer <token>`
  - Request body: `{ "message": "string", "chatId": "string" }`

## Data Models

### User
```json
{
  "id": "string",
  "username": "string",
  "telegramId": "string",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### Chat State
```json
{
  "chatId": "string",
  "state": "string",
  "data": {},
  "lastUpdated": "timestamp"
}
```

### Message
```json
{
  "id": "string",
  "chatId": "string",
  "text": "string",
  "timestamp": "timestamp",
  "processed": "boolean"
}
```

## Error Handling

### HTTP Status Codes
- 200: Success
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 500: Internal Server Error

### Error Response Format
```json
{
  "error": {
    "code": "string",
    "message": "string",
    "details": {}
  }
}
```

## Security Considerations

### Authentication
- All API requests must include a valid JWT token
- Tokens are obtained through the login endpoint
- Tokens expire after a configured time period

### Data Protection
- Sensitive data is encrypted in transit using HTTPS
- User credentials are hashed before storage
- API keys and tokens are stored securely

## Rate Limiting
The backend implements rate limiting to prevent abuse:
- 100 requests per minute per IP
- 1000 requests per hour per user
- Rate limit headers are included in responses

## Webhook Support
The backend supports webhook notifications for real-time updates:
- **POST** `/api/webhooks/register`
  - Registers a webhook URL
  - Required headers: `Authorization: Bearer <token>`
  - Request body: `{ "url": "string", "events": ["string"] }`

## Monitoring and Logging
The backend provides monitoring endpoints:
- **GET** `/api/health`
  - Health check endpoint
- **GET** `/api/metrics`
  - Performance metrics
- **GET** `/api/logs`
  - Application logs (requires admin access)

## Development Guidelines

### Local Development
1. Start the backend service:
   ```bash
   npm install
   npm run dev
   ```

2. Configure environment variables:
   ```env
   PORT=3000
   JWT_SECRET=your_secret
   DATABASE_URL=your_database_url
   ```

3. Run tests:
   ```bash
   npm test
   ```

### Production Deployment
1. Build the application:
   ```bash
   npm run build
   ```

2. Start the production server:
   ```bash
   npm start
   ```

## Troubleshooting

### Common Issues
1. Connection refused
   - Check if the backend service is running
   - Verify the port number
   - Check firewall settings

2. Authentication errors
   - Verify token validity
   - Check token expiration
   - Ensure correct credentials

3. Rate limiting
   - Monitor request frequency
   - Implement request queuing
   - Consider caching responses 