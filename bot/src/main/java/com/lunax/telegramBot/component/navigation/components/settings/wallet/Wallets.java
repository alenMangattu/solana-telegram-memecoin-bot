package com.lunax.telegramBot.component.navigation.components.settings.wallet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.Buttons.wallet.CreateNewWallet;
import com.lunax.telegramBot.component.Buttons.wallet.ImportExistingWallet;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.WalletService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.component.navigation.components.Home;

@Component
public class Wallets implements StateHandler {

    @Autowired
    private UserService userService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private WalletService walletService;
    
    @Autowired
    private SolanaUtils solanaUtils;
    
    @Autowired
    private StateService stateService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private CreateNewWallet createNewWallet;
    
    @Autowired
    private ImportExistingWallet importExistingWallet;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public void executeVoid(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Fetch and sort wallets by creation date (oldest first)
        List<Wallet> wallets = walletService.findByUser(user);
        wallets.sort(Comparator.comparing(Wallet::getCreatedAt));
        
        // Create a cleaner text-based wallet listing
        StringBuilder message = new StringBuilder("*📋 YOUR WALLETS*\n\n");
        
        if (wallets.isEmpty()) {
            message.append("_You don't have any wallets yet. Create or import a wallet to get started._");
        } else {
            message.append("You have ").append(wallets.size()).append(" wallet(s)\n\n");
            
            // List all wallets in order with a green icon for the current one
            for (int i = 0; i < wallets.size(); i++) {
                Wallet wallet = wallets.get(i);
                
                message.append(i + 1).append(". ");
                
                // Mark current wallet with green circle and label
                if (wallet.getWalletId().equals(user.getCurrentWallet())) {
                    message.append("🟢 (Current Wallet) ");
                }
                
                // Wallet label
                if (wallet.getWalletLabel() != null && !wallet.getWalletLabel().isEmpty()) {
                    message.append("*").append(wallet.getWalletLabel()).append("*\n");
                } else {
                    message.append("*Unnamed Wallet*\n");
                }
                
                // Wallet address
                String displayAddress = wallet.getWalletAddress();
                message.append("🔑 `").append(displayAddress).append("`\n");
                // Wallet balance
                try {
                    double balanceValue = solanaUtils.getBalance(wallet.getWalletAddress());
                    String balance = String.valueOf(balanceValue);
                    message.append("💰 `").append(balance).append(" SOL`\n");
                } catch (Exception e) {
                    message.append("💰 Balance: Unable to fetch\n");
                }
                
                message.append("\n");
            }
        }
        
        message.append("*Choose an action below:*");
        
        // Create action buttons
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Add row for creating/importing wallets
        List<InlineKeyboardButton> actionsRow = new ArrayList<>();
        
        // Create new wallet button
        InlineKeyboardButton createWallet = new InlineKeyboardButton();
        createWallet.setText(createNewWallet.getButtonText());
        createWallet.setCallbackData(createNewWallet.getCallbackData());
        actionsRow.add(createWallet);
        
        // Import wallet button
        InlineKeyboardButton importWallet = new InlineKeyboardButton();
        importWallet.setText(importExistingWallet.getButtonText());
        importWallet.setCallbackData(importExistingWallet.getCallbackData());
        actionsRow.add(importWallet);
        
        keyboard.add(actionsRow);
        
        // Add wallet management buttons if wallets exist
        if (!wallets.isEmpty()) {
            // Add wallet label editing button
            List<InlineKeyboardButton> editRow = new ArrayList<>();
            InlineKeyboardButton editButton = new InlineKeyboardButton();
            editButton.setText("✏️ Edit Label");
            editButton.setCallbackData("edit_wallet_menu");
            editRow.add(editButton);
            
            // Add switch current wallet button
            if (wallets.size() > 1) {
                InlineKeyboardButton switchButton = new InlineKeyboardButton();
                switchButton.setText("🔄 Switch Wallet");
                switchButton.setCallbackData("switch_wallet");
                editRow.add(switchButton);
            }
            
            keyboard.add(editRow);
            
            // Add delete wallet button
            List<InlineKeyboardButton> deleteRow = new ArrayList<>();
            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("🗑️ Delete Wallet");
            deleteButton.setCallbackData("delete_wallet_menu");
            deleteRow.add(deleteButton);
            keyboard.add(deleteRow);
        }
        
        // Add back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Back to Settings");
        backButton.setCallbackData("settings");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        // Convert to ButtonHandler[][] for the existing editMessage method
        ButtonHandler[][] buttonHandlers = convertKeyboardToButtonHandlers(keyboard);
        
        // Send the message with the keyboard
        MessageUtils.editMessage(update, message.toString(), buttonHandlers);
        
        // Register callback handlers for the wallet management actions
        registerCallbackHandlers();
    }
    
    // Helper method to convert keyboard to button handlers
    private ButtonHandler[][] convertKeyboardToButtonHandlers(List<List<InlineKeyboardButton>> keyboard) {
        ButtonHandler[][] buttonHandlers = new ButtonHandler[keyboard.size()][];
        for (int i = 0; i < keyboard.size(); i++) {
            List<InlineKeyboardButton> row = keyboard.get(i);
            buttonHandlers[i] = new ButtonHandler[row.size()];
            
            for (int j = 0; j < row.size(); j++) {
                InlineKeyboardButton button = row.get(j);
                final String btnText = button.getText();
                final String btnCallback = button.getCallbackData();
                
                buttonHandlers[i][j] = new ButtonHandler() {
                    @Override
                    public String getButtonText() {
                        return btnText;
                    }
                    
                    @Override
                    public String getCallbackData() {
                        return btnCallback;
                    }
                    
                    @Override
                    public void handleCallback(Update update) {
                        // This won't be called directly
                    }
                };
            }
        }
        return buttonHandlers;
    }
    
    private void registerCallbackHandlers() {
        // Register handlers for the wallet management callbacks
        MessageUtils.registerCallbackHandler("edit_wallet_label:(.*)", this::handleEditWalletLabel);
        MessageUtils.registerCallbackHandler("set_current_wallet:(.*)", this::handleSetCurrentWallet);
        MessageUtils.registerCallbackHandler("delete_wallet:(.*)", this::handleDeleteWallet);
        MessageUtils.registerCallbackHandler("switch_wallet", this::handleSwitchWallet);
        MessageUtils.registerCallbackHandler("edit_wallet_menu", this::handleEditWalletMenu);
        MessageUtils.registerCallbackHandler("delete_wallet_menu", this::handleDeleteWalletMenu);
    }
    
    /**
     * Handles the edit wallet label action
     * @param update The update
     * @param matcher The regex matcher containing the wallet ID
     */
    public void handleEditWalletLabel(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String walletId = matcher.group(1);
        
        // Set state to wait for new label
        stateService.setState(telegramId, ChatState.AWAITING_WALLET_LABEL);
        
        // Create a proper ObjectNode instead of a String
        ObjectNode stateData = objectMapper.createObjectNode();
        stateData.put("walletId", walletId);
        stateService.setStateData(telegramId, stateData);
        
        MessageUtils.editMessage(update, "Please enter a new label for your wallet:", null);
    }
    
    /**
     * Handles setting a wallet as the current wallet
     * @param update The update
     * @param matcher The regex matcher containing the wallet ID
     */
    public void handleSetCurrentWallet(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String walletId = matcher.group(1);
        
        try {
            // Set as current wallet
            User user = userService.findByTelegramId(telegramId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            user.setCurrentWallet(UUID.fromString(walletId));
            userRepository.save(user);
            
            // Show success message and refresh wallet list
            MessageUtils.sendMessage(update, "✅ Current wallet updated successfully");
            executeVoid(update);
            
        } catch (Exception e) {
            MessageUtils.sendMessage(update, "❌ Error setting current wallet: " + e.getMessage());
        }
    }
    
    /**
     * Handles the wallet deletion action
     * @param update The update
     * @param matcher The regex matcher containing the wallet ID
     */
    public void handleDeleteWallet(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String walletId = matcher.group(1);
        
        System.out.println("DELETING WALLET - About to evict cache for user: " + telegramId);
        
        try {
            User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Delete the wallet
            walletService.deleteWallet(user, walletId);
            
            // Clear caches
            clearCacheForUser(telegramId);
            
            // Clear session cache in the service
            walletService.clearSessionCache();
            
            MessageUtils.sendMessage(update, "✅ Wallet deleted successfully.");
            executeVoid(update);
            
            System.out.println("WALLET DELETED - Cache for user " + telegramId + " successfully evicted");
        } catch (Exception e) {
            MessageUtils.sendMessage(update, "❌ Error deleting wallet: " + e.getMessage());
        }
    }
    
    /**
     * Handles the switch wallet action (cycles to next wallet)
     * @param update The update
     * @param matcher The regex matcher
     */
    public void handleSwitchWallet(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        try {
            // Get user and wallets
            User user = userService.findByTelegramId(telegramId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            List<Wallet> userWallets = walletService.findByUser(user);
            
            if (userWallets.size() <= 1) {
                MessageUtils.sendMessage(update, "You need at least two wallets to switch between them.");
                return;
            }
            
            // Find current wallet index
            int currentIndex = -1;
            for (int i = 0; i < userWallets.size(); i++) {
                if (userWallets.get(i).getWalletId().equals(user.getCurrentWallet())) {
                    currentIndex = i;
                    break;
                }
            }
            
            // Get next wallet in circular fashion
            int nextIndex = (currentIndex + 1) % userWallets.size();
            UUID nextWalletId = userWallets.get(nextIndex).getWalletId();
            
            // Update current wallet
            user.setCurrentWallet(nextWalletId);
            userRepository.save(user);
            
            // After updating user's current wallet
            // Home home = ApplicationContextProvider.getBean(Home.class);
            // home.clearCacheForUser(telegramId);
            
            // Clear session cache
            walletService.clearSessionCache();
            
            // Refresh wallet list
            executeVoid(update);
            
        } catch (Exception e) {
            MessageUtils.sendMessage(update, "❌ Error switching wallet: " + e.getMessage());
        }
    }
    
    /**
     * Handles the edit wallet menu display
     * @param update The update
     * @param matcher The regex matcher
     */
    public void handleEditWalletMenu(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Wallet> wallets = walletService.findByUser(user);
        if (wallets.isEmpty()) {
            MessageUtils.sendMessage(update, "❌ You don't have any wallets to edit.");
            return;
        }
        
        StringBuilder message = new StringBuilder("*Select a wallet to edit:*\n\n");
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        for (int i = 0; i < wallets.size(); i++) {
            Wallet wallet = wallets.get(i);
            String label = (wallet.getWalletLabel() != null && !wallet.getWalletLabel().isEmpty()) 
                ? wallet.getWalletLabel() : "Unnamed Wallet";
            
            // Add wallet number and label to message
            message.append(i + 1).append(". ").append(label);
            if (wallet.getWalletId().equals(user.getCurrentWallet())) {
                message.append(" (Current)");
            }
            message.append("\n");
            
            // Add button for this wallet
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("✏️ Edit #" + (i + 1));
            button.setCallbackData("edit_wallet_label:" + wallet.getWalletId());
            row.add(button);
            keyboard.add(row);
        }
        
        // Add back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Back");
        backButton.setCallbackData("wallets");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        ButtonHandler[][] buttonHandlers = convertKeyboardToButtonHandlers(keyboard);
        MessageUtils.editMessage(update, message.toString(), buttonHandlers);
    }
    
    /**
     * Handles the delete wallet menu display
     * @param update The update
     * @param matcher The regex matcher
     */
    public void handleDeleteWalletMenu(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Wallet> wallets = walletService.findByUser(user);
        if (wallets.isEmpty()) {
            MessageUtils.sendMessage(update, "❌ You don't have any wallets to delete.");
            return;
        }
        
        // If user only has one wallet, don't allow deletion
        if (wallets.size() == 1) {
            MessageUtils.sendMessage(update, "❌ You cannot delete your only wallet. Please create or import another wallet first.");
            return;
        }
        
        StringBuilder message = new StringBuilder("*Select a wallet to delete:*\n\n");
        message.append("⚠️ WARNING: This action cannot be undone!\n\n");
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        for (int i = 0; i < wallets.size(); i++) {
            Wallet wallet = wallets.get(i);
            String label = (wallet.getWalletLabel() != null && !wallet.getWalletLabel().isEmpty()) 
                ? wallet.getWalletLabel() : "Unnamed Wallet";
            
            // Skip current wallet as it can't be deleted
            if (wallet.getWalletId().equals(user.getCurrentWallet())) {
                message.append(i + 1).append(". ").append(label).append(" (Current - cannot delete)\n");
                continue;
            }
            
            // Add wallet number and label to message
            message.append(i + 1).append(". ").append(label).append("\n");
            
            // Add button for this wallet
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("🗑️ Delete #" + (i + 1));
            button.setCallbackData("delete_wallet:" + wallet.getWalletId());
            row.add(button);
            keyboard.add(row);
        }
        
        // Add back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Back");
        backButton.setCallbackData("wallets");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        ButtonHandler[][] buttonHandlers = convertKeyboardToButtonHandlers(keyboard);
        MessageUtils.editMessage(update, message.toString(), buttonHandlers);
    }

    // Add a proper method to clear the cache
    public void clearCacheForUser(Long telegramId) {
        // Clear cache programmatically since this is called from other components
        Cache cache = cacheManager.getCache("userWallets");
        if (cache != null) {
            User user = userService.findByTelegramId(telegramId)
                .orElse(null);
            if (user != null) {
                cache.evict(user.getId());
                System.out.println("Cache cleared for user ID: " + user.getId());
            }
        }
    }
}
