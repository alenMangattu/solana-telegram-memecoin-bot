# Troubleshooting Guide

## Common Issues and Solutions

### 1. Bot Not Responding

#### Symptoms
- Bot doesn't respond to messages
- Commands are not recognized
- No error messages in logs

#### Solutions
1. Check if the bot is running:
   ```bash
   # Check running processes
   ps aux | grep java
   
   # Check application logs
   tail -f logs/application.log
   ```

2. Verify bot token:
   ```java
   // Check if token is properly set
   String token = botInstance.getBotToken();
   if (token == null || token.isEmpty()) {
       log.error("Bot token is not set!");
   }
   ```

3. Check backend connection:
   ```bash
   # Test backend health
   curl http://localhost:3000/api/health
   ```

### 2. Message Delivery Issues

#### Symptoms
- Messages not being sent
- Messages stuck in queue
- Delivery timeouts

#### Solutions
1. Check message formatting:
   ```java
   // Ensure proper message formatting
   MessageUtils.sendMessage(update, "Test message");
   ```

2. Verify chat ID:
   ```java
   // Log chat ID for debugging
   log.debug("Chat ID: {}", message.getChatId());
   ```

3. Check rate limits:
   ```java
   // Add delay between messages
   Thread.sleep(1000); // 1 second delay
   ```

### 3. State Management Problems

#### Symptoms
- Incorrect state transitions
- Lost state information
- Stuck in wrong state

#### Solutions
1. Log state changes:
   ```java
   log.info("State transition: {} -> {}", currentState, nextState);
   ```

2. Verify state persistence:
   ```java
   // Check state storage
   ChatState state = getCurrentState(chatId);
   if (state == null) {
       log.error("No state found for chat: {}", chatId);
   }
   ```

3. Reset state if needed:
   ```java
   // Reset to initial state
   setChatState(chatId, ChatState.INITIAL);
   ```

### 4. Backend Integration Issues

#### Symptoms
- API calls failing
- Authentication errors
- Timeout issues

#### Solutions
1. Check API connectivity:
   ```bash
   # Test API endpoint
   curl -X POST http://localhost:3000/api/health \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

2. Verify authentication:
   ```java
   // Check token validity
   if (!isTokenValid(token)) {
       log.error("Invalid token");
       refreshToken();
   }
   ```

3. Handle timeouts:
   ```java
   // Set appropriate timeout
   client.setConnectTimeout(5000);
   client.setReadTimeout(5000);
   ```

### 5. Performance Issues

#### Symptoms
- Slow response times
- High memory usage
- Connection pool exhaustion

#### Solutions
1. Monitor response times:
   ```java
   long startTime = System.currentTimeMillis();
   // Your code here
   long endTime = System.currentTimeMillis();
   log.info("Operation took {} ms", endTime - startTime);
   ```

2. Check memory usage:
   ```java
   Runtime runtime = Runtime.getRuntime();
   long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
   log.info("Used memory: {} MB", usedMemory);
   ```

3. Optimize database queries:
   ```java
   // Use connection pooling
   @Autowired
   private DataSource dataSource;
   ```

### 6. Development Environment Issues

#### Symptoms
- Build failures
- Test failures
- Dependency conflicts

#### Solutions
1. Clean and rebuild:
   ```bash
   mvn clean install -U
   ```

2. Check dependencies:
   ```bash
   mvn dependency:tree
   ```

3. Update dependencies:
   ```bash
   mvn versions:update-parent
   ```

## Debugging Tools

### 1. Logging Configuration
```properties
# application.properties
logging.level.root=INFO
logging.level.com.lunax.telegramBot=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### 2. Debug Mode
```bash
# Run in debug mode
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

### 3. Health Checks
```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", new Date().toString());
        return ResponseEntity.ok(status);
    }
}
```

## Getting Additional Help

### 1. Check Logs
- Application logs: `logs/application.log`
- Backend logs: `backend/logs/server.log`
- System logs: `/var/log/syslog`

### 2. Common Support Channels
- Team chat
- Issue tracker
- Documentation
- Code reviews

### 3. Escalation Process
1. Check documentation
2. Ask team members
3. Create issue ticket
4. Contact system administrator 