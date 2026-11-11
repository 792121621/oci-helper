package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 选择配置回调处理器 - 显示实例方案
 * 
 * @author yohann
 */
@Slf4j
@Component
public class SelectConfigHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String callbackData = callbackQuery.getData();
        String userId = callbackData.split(":")[1];
        
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        OciUser user = userService.getById(userId);
        
        if (user == null) {
            return buildEditMessage(
                    callbackQuery,
                    "❌ 配置不存在",
                    new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())
            );
        }
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        // 添加"创建实例"、"实例管理"和"引导卷管理"选项
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button(
                        "🚀 创建实例",
                        "show_create_plans:" + userId
                )
        ));
        
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button(
                        "📋 实例管理",
                        "instance_management:" + userId
                ),
                KeyboardBuilder.button(
                        "💾 引导卷管理",
                        "boot_volume_management:" + userId
                )
        ));

        // 返回按钮
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回配置列表", "config_list")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        // Format tenant create time
        String tenantCreateTimeStr = user.getTenantCreateTime() != null 
                ? user.getTenantCreateTime().toString().replace("T", " ")
                : "未知";
        
        String message = String.format(
                "【配置操作】\n\n" +
                "🔑 配置名：%s\n" +
                "🌏 区域：%s\n" +
                "👤 租户名：%s\n" +
                "📅 租户创建时间：%s\n\n" +
                "请选择操作：",
                user.getUsername(),
                user.getOciRegion(),
                user.getTenantName() != null ? user.getTenantName() : "未知",
                tenantCreateTimeStr
        );
        
        return buildEditMessage(
                callbackQuery,
                message,
                new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "select_config:";
    }
}
