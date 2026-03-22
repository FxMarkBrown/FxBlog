package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.entity.SysAiMessage;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.mapper.SysAiConversationMapper;
import top.fxmarkbrown.blog.mapper.SysAiMessageMapper;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.AiChatModelService;
import top.fxmarkbrown.blog.service.SysAiConversationService;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.ai.AiConversationAdminVo;
import top.fxmarkbrown.blog.vo.ai.AiMessageVo;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysAiConversationServiceImpl implements SysAiConversationService {

    private final SysAiConversationMapper conversationMapper;
    private final SysAiMessageMapper messageMapper;
    private final SysUserMapper userMapper;
    private final SysArticleMapper articleMapper;
    private final AiChatModelService aiChatModelService;

    @Override
    public IPage<AiConversationAdminVo> pageConversations(String type, String keyword, String userKeyword) {
        Page<SysAiConversation> pageRequest = PageUtil.getPage();
        LambdaQueryWrapper<SysAiConversation> wrapper = new LambdaQueryWrapper<SysAiConversation>()
                .orderByDesc(SysAiConversation::getLastMessageAt)
                .orderByDesc(SysAiConversation::getId);

        if (StringUtils.hasText(type)) {
            wrapper.eq(SysAiConversation::getType, type.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(q -> q.like(SysAiConversation::getTitle, normalizedKeyword)
                    .or()
                    .like(SysAiConversation::getSummary, normalizedKeyword));
        }
        if (StringUtils.hasText(userKeyword)) {
            String normalizedUserKeyword = userKeyword.trim();
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getNickname, normalizedUserKeyword)
                            .or()
                            .like(SysUser::getUsername, normalizedUserKeyword))
                    .stream()
                    .map(SysUser::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (matchedUserIds.isEmpty()) {
                return emptyConversationPage(pageRequest);
            }
            wrapper.in(SysAiConversation::getUserId, matchedUserIds);
        }

        Page<SysAiConversation> page = conversationMapper.selectPage(pageRequest, wrapper);
        if (page.getRecords().isEmpty()) {
            return emptyConversationPage(page);
        }

        List<Long> userIds = page.getRecords().stream()
                .map(SysAiConversation::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, SysUser> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<Long> articleIds = page.getRecords().stream()
                .map(SysAiConversation::getArticleId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, String> articleTitleMap = articleIds.isEmpty()
                ? Collections.emptyMap()
                : articleMapper.selectByIds(articleIds)
                .stream()
                .collect(Collectors.toMap(SysArticle::getId, SysArticle::getTitle));

        Map<Long, Long> messageCountMap = messageMapper.selectList(new LambdaQueryWrapper<SysAiMessage>()
                        .select(SysAiMessage::getConversationId)
                        .in(SysAiMessage::getConversationId, page.getRecords().stream().map(SysAiConversation::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(SysAiMessage::getConversationId, Collectors.counting()));

        Page<AiConversationAdminVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(item -> toAdminVo(item, userMap, articleTitleMap, messageCountMap)).toList());
        return result;
    }

    @Override
    public IPage<AiMessageVo> pageMessages(Long conversationId) {
        Page<SysAiMessage> page = messageMapper.selectPage(
                PageUtil.getPage(),
                new LambdaQueryWrapper<SysAiMessage>()
                        .eq(SysAiMessage::getConversationId, conversationId)
                        .orderByAsc(SysAiMessage::getCreateTime)
                        .orderByAsc(SysAiMessage::getId)
        );
        Page<AiMessageVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toMessageVo).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversations(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        messageMapper.delete(new LambdaQueryWrapper<SysAiMessage>().in(SysAiMessage::getConversationId, ids));
        conversationMapper.deleteByIds(ids);
    }

    private Page<AiConversationAdminVo> emptyConversationPage(Page<?> sourcePage) {
        Page<AiConversationAdminVo> result = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), 0);
        result.setRecords(Collections.emptyList());
        return result;
    }

    private AiConversationAdminVo toAdminVo(
            SysAiConversation conversation,
            Map<Long, SysUser> userMap,
            Map<Long, String> articleTitleMap,
            Map<Long, Long> messageCountMap
    ) {
        SysUser user = userMap.get(conversation.getUserId());
        AiConversationAdminVo vo = new AiConversationAdminVo();
        vo.setId(conversation.getId());
        vo.setUserId(conversation.getUserId());
        vo.setUserNickname(user == null ? "-" : user.getNickname());
        vo.setUserAvatar(user == null ? null : user.getAvatar());
        vo.setType(conversation.getType());
        vo.setArticleId(conversation.getArticleId());
        vo.setArticleTitle(articleTitleMap.getOrDefault(conversation.getArticleId(), "-"));
        vo.setTitle(conversation.getTitle());
        vo.setSummary(conversation.getSummary());
        vo.setModelProvider(conversation.getModelProvider());
        vo.setModelName(conversation.getModelName());
        vo.setModelId(aiChatModelService.resolveModelId(conversation.getModelProvider(), conversation.getModelName()));
        vo.setModelDisplayName(aiChatModelService.resolveDisplayName(conversation.getModelProvider(), conversation.getModelName()));
        vo.setMessageCount(messageCountMap.getOrDefault(conversation.getId(), 0L));
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setCreateTime(conversation.getCreateTime());
        vo.setUpdateTime(conversation.getUpdateTime());
        return vo;
    }

    private AiMessageVo toMessageVo(SysAiMessage message) {
        AiMessageVo vo = new AiMessageVo();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setTokensIn(message.getTokensIn());
        vo.setTokensOut(message.getTokensOut());
        vo.setQuotePayload(message.getQuotePayload());
        vo.setToolCalls(parseToolCalls(message.getQuotePayload()));
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private List<AiToolCallVo> parseToolCalls(String quotePayload) {
        if (quotePayload == null || quotePayload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JsonUtil.readTree(quotePayload);
            JsonNode toolCallsNode = root == null ? null : root.get("toolCalls");
            if (toolCallsNode == null || !toolCallsNode.isArray()) {
                return List.of();
            }
            List<AiToolCallVo> toolCalls = JsonUtil.convertValue(toolCallsNode, new TypeReference<>() {
            });
            return toolCalls == null ? List.of() : toolCalls;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
