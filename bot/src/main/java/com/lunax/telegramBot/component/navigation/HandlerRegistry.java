package com.lunax.telegramBot.component.navigation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.AskMemeCoinData;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.InitialLiquidity.AddLiquidity;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.YesOrNoButtons.RevokeFreezeAuthority;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.YesOrNoButtons.RevokeMintAuthority;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.component.navigation.components.mymemecoin.PaginationButtonHandler;
import com.lunax.telegramBot.component.navigation.components.token.TradePaginationButtonHandler;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.TradeMemeCoinButton;
import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;
import com.lunax.telegramBot.component.navigation.components.trading.CopyTradingHandler;
import com.lunax.telegramBot.service.CopyTradingHandlerService;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.function.BiConsumer;

import org.telegram.telegrambots.meta.api.objects.Update;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HandlerRegistry {
    private final Map<String, ButtonHandler> handlers = new HashMap<>();
    private final Map<String, StateHandler> stateHandlers = new HashMap<>();
    
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CopyTradingHandlerService copyTradingHandlerService;

    private static final Logger log = LoggerFactory.getLogger(HandlerRegistry.class);

    private final Map<String, List<BiConsumer<Update, String>>> dynamicHandlers = new HashMap<>();

    @Autowired
    public HandlerRegistry(
            AskMemeCoinData askMemeCoinData,
            AddLiquidity addLiquidity,
            RevokeMintAuthority revokeMintAuthority,
            RevokeFreezeAuthority revokeFreezeAuthority,
            PaginationButtonHandler paginationButtonHandler,
            TradeMemeCoinButton tradeMemeCoinButton,
            TradePaginationButtonHandler tradePaginationButtonHandler) {
        
        handlers.put(askMemeCoinData.getCallbackData(), askMemeCoinData);
        handlers.put(addLiquidity.getCallbackData(), addLiquidity);
        handlers.put("revoke_mint_authority_confirm", revokeMintAuthority);
        handlers.put("revoke_freeze_authority_confirm", revokeFreezeAuthority);
        handlers.put("page_", paginationButtonHandler);
        handlers.put("trade_meme_coin", tradeMemeCoinButton);
        handlers.put("trade_page_", tradePaginationButtonHandler);
        handlers.put("revoke_mint_authority_cancel", new CancelMintAuthority(revokeMintAuthority));
    }
    
    private static class CancelMintAuthority implements ButtonHandler {
        private final RevokeMintAuthority revokeMintAuthority;

        CancelMintAuthority(RevokeMintAuthority revokeMintAuthority) {
            this.revokeMintAuthority = revokeMintAuthority;
        }

        @Override
        public String getButtonText() { return "No"; }

        @Override
        public String getCallbackData() { return "revoke_mint_authority_cancel"; }

        @Override
        public void handleCallback(Update update) {
            revokeMintAuthority.handleCancel(update);
        }
    }
    
    @PostConstruct
    public void init() {
        // Get all beans that implement ButtonHandler
        Map<String, ButtonHandler> handlerBeans = applicationContext.getBeansOfType(ButtonHandler.class);
        
        // Register each handler by its callback data
        handlerBeans.values().forEach(handler -> 
            handlers.put(handler.getCallbackData(), handler)
        );
        
        // Get all beans that implement StateHandler
        Map<String, StateHandler> stateHandlerBeans = applicationContext.getBeansOfType(StateHandler.class);
        
        // Register each state handler by its state name
        stateHandlerBeans.values().forEach(handler -> {
            if (handler.getClass().getSimpleName() != null) {
                String stateName = handler.getClass().getSimpleName().toUpperCase();
                if (stateName.equals("TOKENINFO")) {
                    registerStateHandler(ChatState.TOKEN_INFO_PAGE.toString(), handler);
                } else {
                    try {
                        ChatState state = ChatState.valueOf(stateName);
                        registerStateHandler(state.toString(), handler);
                    } catch (IllegalArgumentException e) {
                        // Not a standard state name, ignore
                    }
                }
            }
        });

        // Copy trading button handler
        registerHandler("copy_trading_page", new ButtonHandler() {
            @Override
            public void handleCallback(Update update) {
                copyTradingHandlerService.handleCopyTradingButton(update);
            }

            @Override
            public String getButtonText() {
                return "Copy Trading Page";
            }

            @Override
            public String getCallbackData() {
                return "copy_trading_page";
            }
        });

        // Add copy trading config handler
        registerHandler("add_copy_trading_config", new ButtonHandler() {
            @Override
            public void handleCallback(Update update) {
                copyTradingHandlerService.handleAddCopyTradingConfig(update);
            }

            @Override
            public String getButtonText() {
                return "Add New Configuration";
            }

            @Override
            public String getCallbackData() {
                return "add_copy_trading_config";
            }
        });

        // Handle "back_to_trading" button
        registerHandler("back_to_trading", new ButtonHandler() {
            @Override
            public void handleCallback(Update update) {
                getTradeMemeCoin().executeVoid(update);
            }

            @Override
            public String getButtonText() {
                return "Back to Trading";
            }

            @Override
            public String getCallbackData() {
                return "back_to_trading";
            }
        });

        // Dynamic handlers for toggle and delete actions
        registerDynamicHandler("toggle_copy_config_", (update, data) -> {
            String[] parts = data.split("_");
            Long configId = Long.parseLong(parts[3]);
            boolean newState = Boolean.parseBoolean(parts[4]);
            copyTradingHandlerService.handleToggleCopyConfig(update, configId, newState);
        });

        registerDynamicHandler("delete_copy_config_", (update, data) -> {
            String[] parts = data.split("_");
            Long configId = Long.parseLong(parts[3]);
            copyTradingHandlerService.handleDeleteCopyConfig(update, configId);
        });
    }
    
    public ButtonHandler getHandler(String callbackData) {
        // First try exact match
        ButtonHandler handler = handlers.get(callbackData);
        if (handler != null) {
            return handler;
        }
        
        // If no exact match, try prefix match
        return handlers.entrySet().stream()
            .filter(entry -> !entry.getKey().isEmpty() && callbackData.startsWith(entry.getKey()))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(null);
    }
    
    public void registerHandler(String callbackData, ButtonHandler handler) {
        handlers.put(callbackData, handler);
        log.info("Registered handler for callback data: {}", callbackData);
    }
    
    public Map<String, ButtonHandler> getAllHandlers() {
        return handlers;
    }

    public StateHandler getHandler(ChatState state) {
        return getStateHandler(state.toString());
    }

    public void registerStateHandler(String stateName, StateHandler handler) {
        stateHandlers.put(stateName, handler);
    }

    public StateHandler getStateHandler(String stateName) {
        return stateHandlers.get(stateName);
    }

    public void registerDynamicHandler(String prefix, BiConsumer<Update, String> handler) {
        dynamicHandlers.computeIfAbsent(prefix, k -> new ArrayList<>()).add(handler);
    }

    public void handleDynamicCallback(String callbackData, Update update) {
        for (Map.Entry<String, List<BiConsumer<Update, String>>> entry : dynamicHandlers.entrySet()) {
            String prefix = entry.getKey();
            if (callbackData.startsWith(prefix)) {
                for (BiConsumer<Update, String> handler : entry.getValue()) {
                    handler.accept(update, callbackData);
                }
                return;
            }
        }
    }

    private TradeMemeCoin getTradeMemeCoin() {
        return applicationContext.getBean(TradeMemeCoin.class);
    }
}
