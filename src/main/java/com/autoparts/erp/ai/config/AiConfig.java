package com.autoparts.erp.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient aiChatClient(ChatClient.Builder builder) {

        return builder
                .defaultSystem("""
                    You are an AI assistant for an Auto Parts ERP system.

                    Help users with:
                    - Products
                    - Inventory
                    - Purchases
                    - Sales
                    - Customers
                    - Suppliers

                    Never invent database information.
                    Never claim an operation was completed
                    unless the backend confirms it.
                    """)
                .build();
    }
}