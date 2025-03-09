package com.lunax.telegramBot.component.Buttons.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportExistingWallet implements ButtonHandler {

    private final String buttonText;
    private final String callbackData; 
    
    @Autowired
    private StateService stateService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private WalletKeyManager walletKeyManager;
    
    @Autowired
    private SolanaUtils solanaUtils;
    
    @Value("${encryption.keys.path}")
    private String encryptionKeysPath;
    
    @Value("${wallets.path}")
    private String walletsPath;

    public ImportExistingWallet(@Value("📥 Import Existing Wallet") String buttonText, 
                            @Value("import_existing_wallet") String callbackData) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
    } 
    
    @Override
    public String getButtonText() {
        return buttonText;
    }

    @Override
    public String getCallbackData() {
        return callbackData;
    }

    @Override
    // @CacheEvict(value = "homeWalletCache", key = "#update.callbackQuery.from.id")
    @Transactional
    public void handleCallback(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        if (!userService.hasAcceptedTos(telegramId)) {
            MessageUtils.sendMessage(update, "❌ Please accept the Terms of Service before importing a wallet.");
            return;
        }
        
        // Check if user has reached the wallet limit of 8
        User user = userRepository.findByTelegramId(telegramId)
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setTelegramId(telegramId);
                newUser.setAcceptedTos(true);
                return userRepository.save(newUser);
            });
        
        List<Wallet> userWallets = walletRepository.findByUser(user);
        if (userWallets.size() >= 8) {
            MessageUtils.editMessage(update, "❌ You have reached the maximum limit of 8 wallets per user. Please delete an existing wallet before importing a new one.", null);
            return;
        }
        
        String message = """
                *Import Existing Wallet*
                
                To import an existing wallet, please provide your private key or seed phrase.
                
                ⚠️ *Security Warning* ⚠️
                Your private key is sensitive information. Ensure you're in a secure environment before proceeding.
                """;
        
        // Set state to wait for private key input
        stateService.setState(telegramId, ChatState.AWAITING_WALLET_IMPORT);
        ObjectNode stateData = objectMapper.createObjectNode();
        stateService.setStateData(telegramId, stateData);
        
        // Edit current message to ask for private key
        MessageUtils.editMessage(update, message, null);
        
        // Send follow-up message with cancel option
        MessageUtils.sendInlineKeyboard(update, "Please enter your private key or seed phrase:", 
                new ButtonHandler[][]{
                        {
                            new ButtonHandler() {
                                @Override public String getButtonText() { return "❌ Cancel"; }
                                @Override public String getCallbackData() { return "wallets"; }
                                @Override public void handleCallback(Update update) {}
                            }
                        }
                });
    }
    
    /**
     * Process the private key entered by the user
     * This method would be called from a message handler when the user is in AWAITING_WALLET_IMPORT state
     * @param update The Telegram update containing the user's message with private key
     * @param privateKey The private key entered by the user
     */
    public void processPrivateKey(Update update, String privateKey) {
        Long telegramId = update.getMessage().getFrom().getId();
        
        try {
            // Validate the private key format
            if (!isValidPrivateKey(privateKey)) {
                MessageUtils.sendMessage(update, "❌ Invalid private key format. Please check and try again.");
                return;
            }
            
            // Delete the message containing the private key for security
            MessageUtils.deleteMessage(update);
            
            // Send processing message
            MessageUtils.sendMessage(update, "⏳ Processing your wallet import...");
            
            // Get or create user
            User user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramId);
                    newUser.setAcceptedTos(true);
                    return userRepository.save(newUser);
                });
            
            // Check wallet limit again (in case user somehow bypassed the initial check)
            List<Wallet> userWallets = walletRepository.findByUser(user);
            if (userWallets.size() >= 8) {
                MessageUtils.sendMessage(update, "❌ You have reached the maximum limit of 8 wallets per user. Please delete an existing wallet before importing a new one.");
                stateService.setState(telegramId, ChatState.IDLE);
                return;
            }
            
            // Derive public key from private key
            Account account = new Account(Base58.decode(privateKey));
            String publicKey = account.getPublicKey().toString();
            
            // Check if wallet already exists in our system
            if (walletRepository.existsByWalletAddress(publicKey)) {
                MessageUtils.sendMessage(update, "❌ This wallet is already imported into our system.");
                return;
            }
            
            // Create and save wallet
            Wallet wallet = new Wallet();
            wallet.setWalletId(UUID.randomUUID());
            wallet.setWalletAddress(publicKey);
            wallet.setUser(user);
            wallet.setWalletLabel("Imported Wallet");
            wallet.setCreatedAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());
            wallet = walletRepository.save(wallet);
            
            // Store private key securely
            walletKeyManager.storePrivateKey(wallet.getWalletId(), privateKey);
            
            // Set as current wallet
            user.setCurrentWallet(wallet.getWalletId());
            userRepository.save(user);
            
            // Try to get wallet balance
            String balanceInfo = "";
            try {
                double balance = solanaUtils.getBalance(publicKey);
                balanceInfo = String.format("\n\nCurrent balance: `%s SOL`", balance);
            } catch (Exception e) {
                balanceInfo = "\n\nUnable to fetch wallet balance.";
            }
            
            // Send success message
            String successMessage = String.format(
                "✅ Wallet imported successfully!\n\n" +
                "Public Key: `%s`%s\n\n" +
                "Your wallet has been set as your current wallet.",
                publicKey, balanceInfo
            );
            
            MessageUtils.sendMessage(update, successMessage);
            
            // Reset state
            stateService.setState(telegramId, ChatState.IDLE);
            
        } catch (Exception e) {
            // Handle any errors
            MessageUtils.sendMessage(update, "❌ Error importing wallet: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
        }
    }
    
    /**
     * Validate if the provided string is a valid Solana private key
     * @param privateKey The private key to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidPrivateKey(String privateKey) {
        try {
            // Check if it's a valid Base58 string
            byte[] decodedKey = Base58.decode(privateKey);
            
            // Solana private keys should be 64 bytes
            if (decodedKey.length != 64) {
                return false;
            }
            
            // Try to create an account with this key
            new Account(decodedKey);
            
            // If we get here, it's a valid key
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
