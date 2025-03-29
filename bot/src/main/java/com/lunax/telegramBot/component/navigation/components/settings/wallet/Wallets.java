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
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.component.navigation.components.Home;

@Component
public class Wallets implements StateHandler {
    private static final Logger log = LoggerFactory.getLogger(Wallets.class);

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

    @Autowired
    private WalletKeyManager walletKeyManager;

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
            // Add switch current wallet button if multiple wallets exist
            if (wallets.size() > 1) {
                List<InlineKeyboardButton> switchRow = new ArrayList<>();
                InlineKeyboardButton switchButton = new InlineKeyboardButton();
                switchButton.setText("🔄 Switch Wallet");
                switchButton.setCallbackData("switch_wallet");
                switchRow.add(switchButton);
                keyboard.add(switchRow);
            }
            
            // Add wallet configuration button
            List<InlineKeyboardButton> configRow = new ArrayList<>();
            InlineKeyboardButton configButton = new InlineKeyboardButton();
            configButton.setText("⚙️ Wallet Configuration");
            configButton.setCallbackData("wallet_config_menu");
            configRow.add(configButton);
            keyboard.add(configRow);
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
        MessageUtils.registerCallbackHandler("wallet_config_menu", this::handleWalletConfigMenu);
        MessageUtils.registerCallbackHandler("show_private_key:(.*)", this::handleShowPrivateKey);
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

            // Check if current wallet still exists
            boolean currentWalletExists = false;
            for (Wallet wallet : userWallets) {
                if (wallet.getWalletId().equals(user.getCurrentWallet())) {
                    currentWalletExists = true;
                    break;
                }
            }

            // If current wallet doesn't exist anymore, set the first wallet as current
            if (!currentWalletExists) {
                user.setCurrentWallet(userWallets.get(0).getWalletId());
                userRepository.save(user);
                walletService.clearSessionCache();
                executeVoid(update);
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
            
            // Clear session cache
            walletService.clearSessionCache();
            
            // Refresh wallet list
            executeVoid(update);
            
        } catch (Exception e) {
            log.error("Error switching wallet: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error switching wallet. Please try again.");
            // Refresh the wallet list to ensure UI is in sync
            executeVoid(update);
        }
    }
    
    /**
     * Handles the wallet configuration menu display
     * @param update The update
     * @param matcher The regex matcher
     */
    public void handleWalletConfigMenu(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Wallet> wallets = walletService.findByUser(user);
        if (wallets.isEmpty()) {
            MessageUtils.sendMessage(update, "❌ You don't have any wallets to configure.");
            return;
        }
        
        StringBuilder message = new StringBuilder("*⚙️ Wallet Configuration*\n\n");
        message.append("Select a wallet to manage its settings:\n\n");
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        for (int i = 0; i < wallets.size(); i++) {
            Wallet wallet = wallets.get(i);
            String label = (wallet.getWalletLabel() != null && !wallet.getWalletLabel().isEmpty()) 
                ? wallet.getWalletLabel() : "Unnamed Wallet";
            
            // Add wallet number and label to message
            message.append(i + 1).append(". ").append(label);
            if (wallet.getWalletId().equals(user.getCurrentWallet())) {
                message.append(" (Current Wallet cannot be deleted)");
            }
            message.append("\n");
            
            // Create a row of buttons for this wallet
            List<InlineKeyboardButton> walletRow = new ArrayList<>();
            
            // Add wallet number button (non-functional)
            InlineKeyboardButton numberButton = new InlineKeyboardButton();
            numberButton.setText("#" + (i + 1));
            numberButton.setCallbackData("wallet_number:" + wallet.getWalletId());
            walletRow.add(numberButton);
            
            // Add edit label button
            InlineKeyboardButton editButton = new InlineKeyboardButton();
            editButton.setText("✏️ Edit");
            editButton.setCallbackData("edit_wallet_label:" + wallet.getWalletId());
            walletRow.add(editButton);
            
            // Add show private key button
            InlineKeyboardButton keyButton = new InlineKeyboardButton();
            keyButton.setText("🔑 Key");
            keyButton.setCallbackData("show_private_key:" + wallet.getWalletId());
            walletRow.add(keyButton);
            
            // Add delete button (only if not current wallet)
            if (!wallet.getWalletId().equals(user.getCurrentWallet())) {
                InlineKeyboardButton deleteButton = new InlineKeyboardButton();
                deleteButton.setText("🗑️ Delete");
                deleteButton.setCallbackData("delete_wallet:" + wallet.getWalletId());
                walletRow.add(deleteButton);
            }
            
            keyboard.add(walletRow);
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
     * Handles showing the private key for a specific wallet
     * @param update The update
     * @param matcher The regex matcher containing the wallet ID
     */
    public void handleShowPrivateKey(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String walletId = matcher.group(1);
        
        try {
            // Get the private key
            String privateKey = walletKeyManager.getPrivateKey(UUID.fromString(walletId));
            
            // Create a warning message with the private key
            StringBuilder message = new StringBuilder();
            message.append("⚠️ *EXTREME SECURITY WARNING* ⚠️\n\n");
            message.append("🔐 *Your Private Key:*\n");
            message.append("`").append(privateKey).append("`\n\n");
            message.append("⚠️ *IMPORTANT:*\n");
            message.append("• This message will be deleted in 5 minutes\n");
            message.append("• Never share this key with anyone\n");
            message.append("• Anyone with this key can control your wallet\n");
            message.append("• Store this key securely if you need to save it\n");
            message.append("• We recommend using a hardware wallet for better security\n\n");
            message.append("✅ Key will be automatically deleted in 5 minutes");
            
            // Send the message
            Message privateKeyMessage = MessageUtils.sendMessage(update, message.toString());
            
            // Schedule deletion after 5 minutes
            new Thread(() -> {
                try {
                    Thread.sleep(5 * 60 * 1000); // 5 minutes
                    MessageUtils.deleteMessage(privateKeyMessage);
                } catch (InterruptedException e) {
                    log.error("Error in private key deletion thread: {}", e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            MessageUtils.sendMessage(update, "❌ Error retrieving private key: " + e.getMessage());
        }
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
