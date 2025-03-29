package com.lunax.telegramBot.component.navigation.components.trading;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.model.db.LimitOrder;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.TransactionSendingService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ViewLimitOrders implements StateHandler {

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private TransactionSendingService transactionSendingService;
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final int ORDERS_PER_PAGE = 3; // Showing fewer orders per page for better readability
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void executeVoid(Update update) {
        executeVoid(update, 0); // Default to the first page
    }
    
    public void executeVoid(Update update, int page) {
        // Get user ID
        Long telegramId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getFrom().getId() : 
            update.getMessage().getFrom().getId();
            
        // Get user
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        // Set the state
        stateService.setState(telegramId, ChatState.VIEW_LIMIT_ORDERS);
        ObjectNode stateData = objectMapper.createObjectNode();
        stateData.put("currentPage", page);
        stateService.setStateData(telegramId, stateData);
        
        // Get active limit orders for the user
        List<LimitOrder> activeOrders = transactionSendingService.getLimitOrdersByTelegramId(user.getTelegramId());
        log.info("Active orders: {}", activeOrders);
        // Filter to only show pending orders
        activeOrders = activeOrders.stream()
            .filter(order -> order.getStatus().equals("pending") || order.getStatus().equals("active"))
            .collect(Collectors.toList());
        
        // Check if there are any orders
        if (activeOrders.isEmpty()) {
            String noOrdersMessage = """
                    📭 *You have no active limit orders*
                    
                    You can create limit orders when buying a token. Limit orders allow you to:
                    
                    • Buy tokens at a specific price automatically
                    • Set a maximum / minimum price you're willing to pay
                    • Have your orders execute even when you're offline
                    
                    Try creating a limit order by searching for a token and selecting "Create Limit Order" during the purchase process.
                    """;
            
            MessageUtils.sendMessage(update, noOrdersMessage, createNoOrdersKeyboard());
            return;
        }
        
        // Calculate pagination
        int totalOrders = activeOrders.size();
        int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
        int currentPage = Math.min(page, totalPages - 1);
        int start = currentPage * ORDERS_PER_PAGE;
        int end = Math.min(start + ORDERS_PER_PAGE, totalOrders);
        
        // Get the orders for the current page
        List<LimitOrder> ordersOnPage = activeOrders.subList(start, end);
        
        // Format the message
        StringBuilder message = new StringBuilder();
        message.append("⚠️ *COMING SOON: Limit Order Feature*\n");
        message.append("The limit order feature is currently under development and not yet available.\n");
        message.append("Your orders are saved but will not be executed until the feature is released.\n\n");
        message.append("📋 *Your Active Limit Orders* ").append("(Page ").append(currentPage + 1).append(" of ").append(totalPages).append(")\n\n");
        message.append("You have *").append(totalOrders).append("* active orders waiting to execute.\n\n");
        
        // Display orders for the current page
        for (int i = 0; i < ordersOnPage.size(); i++) {
            LimitOrder order = ordersOnPage.get(i);
            
            String orderType = order.getAction().toString();
            String orderIcon = orderType.equals("BUY") ? "🟢" : "🔴";
            
            message.append("*Order #").append(i + 1 + start).append("* ").append(orderIcon).append(" *").append(orderType).append("*\n");
            message.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
            message.append("🆔 ID: `").append(order.getOrderId().substring(order.getOrderId().lastIndexOf("_") + 1)).append("`\n");
            message.append("💰 Amount: ").append(order.getSolAmount()).append(" SOL\n");
            message.append("🪙 Token: `").append(shortenAddress(order.getTokenMint())).append("`\n");
            message.append("💵 Limit Price: $").append(order.getLimitPrice()).append("\n");
            message.append("🛡️ Slippage: ").append(order.getSlippage()).append("%\n");
            message.append("⏱️ Created: ").append(order.getCreatedAt().toLocalDateTime().format(DATE_FORMATTER)).append("\n\n");
        }
        
        message.append("ℹ️ *Tap a button below to manage your orders*");
        
        // Create keyboard with cancel buttons for each order and pagination
        InlineKeyboardMarkup markup = createKeyboard(ordersOnPage, currentPage, totalPages);
        
        // Edit or send message
        if (update.hasCallbackQuery()) {
            try {

                TelegramLongPollingBot bot = (TelegramLongPollingBot) MessageUtils.getBotInstance();
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(String.valueOf(update.getCallbackQuery().getMessage().getChatId()));
                editMessage.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                editMessage.setText(message.toString());
                editMessage.setReplyMarkup(markup);
                editMessage.setParseMode("Markdown");
                // Execute the edit message
                bot.execute(editMessage);
            } catch (TelegramApiException e) {
                log.error("Error editing message: {}", e.getMessage());
            }
        } else {
            MessageUtils.sendMessage(update, message.toString(), markup);
        }
    }
    
    private String shortenAddress(String address) {
        if (address == null || address.length() <= 10) {
            return address;
        }
        return address.substring(0, 5) + "..." + address.substring(address.length() - 5);
    }
    
    private InlineKeyboardMarkup createNoOrdersKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Add button to go back to trading
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
            .text("🔙 Back to Trading")
            .callbackData("back_to_trading")
            .build();
        row.add(backButton);
        
        keyboard.add(row);
        
        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }
    
    private InlineKeyboardMarkup createKeyboard(List<LimitOrder> ordersOnPage, int currentPage, int totalPages) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Add cancel buttons for each order
        for (LimitOrder order : ordersOnPage) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            // Simplify the order ID for display
            String shortOrderId = order.getOrderId().substring(order.getOrderId().lastIndexOf("_") + 1);
            
            // Button to cancel this specific order
            InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text("❌ Cancel " + (order.getAction() == null ? "Order" : order.getAction().toString()) + " #" + shortOrderId)
                .callbackData("cancel_order_" + order.getOrderId())
                .build();
            row.add(cancelButton);
            
            keyboard.add(row);
        }
        
        // Add pagination row if needed
        if (totalPages > 1) {
            List<InlineKeyboardButton> paginationRow = new ArrayList<>();
            
            if (currentPage > 0) {
                // Previous page button
                InlineKeyboardButton prevButton = InlineKeyboardButton.builder()
                    .text("⬅️ Previous")
                    .callbackData("limit_orders_page_" + (currentPage - 1))
                    .build();
                paginationRow.add(prevButton);
            }
            
            // Page indicator button (non-functional, just shows current page)
            InlineKeyboardButton pageIndicator = InlineKeyboardButton.builder()
                .text("📄 " + (currentPage + 1) + "/" + totalPages)
                .callbackData("no_action") // No action for this button
                .build();
            paginationRow.add(pageIndicator);
            
            if (currentPage < totalPages - 1) {
                // Next page button
                InlineKeyboardButton nextButton = InlineKeyboardButton.builder()
                    .text("Next ➡️")
                    .callbackData("limit_orders_page_" + (currentPage + 1))
                    .build();
                paginationRow.add(nextButton);
            }
            
            keyboard.add(paginationRow);
        }
        
        // Add back to trading button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
            .text("🔙 Back to Trading")
            .callbackData("back_to_trading")
            .build();
        backRow.add(backButton);
        
        keyboard.add(backRow);
        
        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }
} 