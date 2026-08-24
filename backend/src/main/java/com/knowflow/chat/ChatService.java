package com.knowflow.chat;

import com.knowflow.ai.ChatModelProvider;
import com.knowflow.ai.EmbeddingProvider;
import com.knowflow.common.BusinessException;
import com.knowflow.knowledge.KnowledgeBaseService;
import com.knowflow.retrieval.ChunkSearchResult;
import com.knowflow.retrieval.DocumentChunkMapper;
import com.knowflow.retrieval.VectorUtils;
import com.knowflow.security.SecurityUtils;
import com.knowflow.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Logger log =
            LoggerFactory.getLogger(ChatService.class);

    /**
     * 检索最低相似度。
     * 低于该值直接拒答。
     */
    private static final double MIN_SIMILARITY = 0.08;

    /**
     * 最多召回的 Chunk 数量。
     */
    private static final int TOP_K = 6;

    /**
     * Query Rewrite 最多读取最近多少条历史消息。
     */
    private static final int REWRITE_HISTORY_LIMIT = 6;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final CitationMapper citationMapper;

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentChunkMapper chunkMapper;

    private final EmbeddingProvider embeddingProvider;
    private final ChatModelProvider chatModelProvider;

    private final Executor chatExecutor;

    private final ChatRateLimitService rateLimitService;

    public ChatService(
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            CitationMapper citationMapper,
            KnowledgeBaseService knowledgeBaseService,
            DocumentChunkMapper chunkMapper,
            EmbeddingProvider embeddingProvider,
            ChatModelProvider chatModelProvider,
            @Qualifier("chatExecutor") Executor chatExecutor,
            ChatRateLimitService rateLimitService
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.citationMapper = citationMapper;

        this.knowledgeBaseService = knowledgeBaseService;
        this.chunkMapper = chunkMapper;

        this.embeddingProvider = embeddingProvider;
        this.chatModelProvider = chatModelProvider;

        this.chatExecutor = chatExecutor;

        this.rateLimitService = rateLimitService;
    }

    /**
     * 获取当前用户历史会话。
     */
    public List<ChatDtos.ConversationView> conversations() {

        UserPrincipal user =
                SecurityUtils.current();

        return conversationMapper
                .listMine(
                        user.tenantId(),
                        user.userId()
                )
                .stream()
                .map(ChatDtos.ConversationView::from)
                .toList();
    }

    /**
     * 创建会话。
     */
    public ChatDtos.ConversationView createConversation(
            ChatDtos.CreateConversationRequest request
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        ConversationEntity entity =
                new ConversationEntity();

        entity.setTenantId(
                user.tenantId()
        );

        entity.setUserId(
                user.userId()
        );

        String title =
                request.title();

        if (
                title == null
                        || title.isBlank()
        ) {
            title = "新对话";
        }

        entity.setTitle(
                title.trim()
        );

        conversationMapper.insert(
                entity
        );

        return ChatDtos.ConversationView.from(
                conversationMapper.selectById(
                        entity.getId()
                )
        );
    }

    /**
     * 获取指定会话历史消息。
     */
    public List<ChatDtos.MessageView> messages(
            Long conversationId
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        requireConversation(
                conversationId,
                user
        );

        return messageMapper
                .listByConversation(
                        conversationId,
                        user.tenantId()
                )
                .stream()
                .map(
                        message ->
                                new ChatDtos.MessageView(
                                        message.getId(),
                                        message.getRole(),
                                        message.getContent(),
                                        message.getStatus(),
                                        message.getCreatedAt(),

                                        citationMapper
                                                .listByMessage(
                                                        message.getId()
                                                )
                                                .stream()
                                                .map(
                                                        citation ->
                                                                new ChatDtos.CitationView(
                                                                        citation.getDocumentId(),
                                                                        citation.getChunkId(),
                                                                        citation.getPageNumber(),
                                                                        citation.getCitationIndex(),
                                                                        citation.getExcerpt(),
                                                                        citation.getSimilarity()
                                                                )
                                                )
                                                .toList()
                                )
                )
                .toList();
    }

    /**
     * 创建 SSE 智能问答。
     */
    public SseEmitter stream(
            ChatDtos.StreamRequest request
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        /*
         * Redis 限流。
         */
        rateLimitService.check(
                user.tenantId(),
                user.userId()
        );

        /*
         * 校验用户是否有权限访问选择的知识库。
         */
        request
                .knowledgeBaseIds()
                .forEach(
                        knowledgeBaseService::require
                );

        Long conversationId =
                request.conversationId();

        /*
         * 没有 conversationId：
         * 使用当前问题自动创建新会话。
         */
        if (conversationId == null) {

            conversationId =
                    createConversation(
                            new ChatDtos.CreateConversationRequest(
                                    shortTitle(
                                            request.question()
                                    )
                            )
                    ).id();

        } else {

            requireConversation(
                    conversationId,
                    user
            );
        }

        final Long finalConversationId =
                conversationId;

        final UserPrincipal snapshot =
                user;

        /*
         * 最大 SSE 时长 180 秒。
         */
        SseEmitter emitter =
                new SseEmitter(
                        180_000L
                );

        /*
         * AI 调用放到独立线程。
         */
        chatExecutor.execute(
                () ->
                        executeStream(
                                snapshot,
                                finalConversationId,
                                request,
                                emitter
                        )
        );

        return emitter;
    }

    /**
     * ============================================================
     * RAG 主流程
     * ============================================================
     */
    protected void executeStream(
            UserPrincipal user,
            Long conversationId,
            ChatDtos.StreamRequest request,
            SseEmitter emitter
    ) {

        try {

            /*
             * ========================================================
             * 1. 多轮问题改写 Query Rewrite
             * ========================================================
             *
             * 必须在保存当前问题之前执行。
             *
             * 例如：
             *
             * 第一轮：
             * 报销超过5000元需要哪些审批？
             *
             * 第二轮：
             * 那5000元以下呢？
             *
             * 改写：
             * 单笔报销金额5000元以下需要哪些审批？
             */
            String retrievalQuestion =
                    rewriteQuestion(
                            user,
                            conversationId,
                            request.question()
                    );

            log.info(
                    "RAG query rewrite: original='{}', rewritten='{}'",
                    request.question(),
                    retrievalQuestion
            );

            /*
             * ========================================================
             * 2. 保存用户原始问题
             * ========================================================
             *
             * 注意：
             * 数据库保存的是用户真正输入的内容，
             * 不是改写后的 retrievalQuestion。
             */
            MessageEntity question =
                    new MessageEntity();

            question.setTenantId(
                    user.tenantId()
            );

            question.setConversationId(
                    conversationId
            );

            question.setRole(
                    "USER"
            );

            question.setContent(
                    request.question()
            );

            question.setStatus(
                    "COMPLETED"
            );

            messageMapper.insert(
                    question
            );

            /*
             * ========================================================
             * 3. Query Embedding
             * ========================================================
             *
             * 使用改写后的完整问题生成 Embedding。
             */
            float[] queryVector =
                    embeddingProvider
                            .embed(
                                    List.of(
                                            retrievalQuestion
                                    )
                            )
                            .getFirst();

            /*
             * ========================================================
             * 4. pgvector 检索
             * ========================================================
             *
             * DocumentChunkMapper SQL 中继续执行：
             *
             * tenant_id 过滤
             * knowledge_base_id 过滤
             * READY 文档过滤
             * current_version 过滤
             */
            List<ChunkSearchResult> contexts =
                chunkMapper.search(
                        user.tenantId(),

                        user.userId(),

                        user.role(),

                        VectorUtils.toPgBigintArray(
                                request.knowledgeBaseIds()
                        ),

                        VectorUtils.toPgVector(
                                queryVector
                        ),

                        TOP_K
                );

            /*
             * ========================================================
             * 5. 无可靠依据时拒答
             * ========================================================
             */
            if (
                    contexts.isEmpty()
                            || contexts
                            .getFirst()
                            .similarity()
                            < MIN_SIMILARITY
            ) {

                String refusal =
                        "当前知识库中未找到足够可靠的依据，"
                                + "建议补充相关文档或调整问题。";

                MessageEntity answer =
                        saveAnswer(
                                user,
                                conversationId,
                                refusal
                        );

                send(
                        emitter,
                        "meta",
                        "{\"conversationId\":"
                                + conversationId
                                + ",\"messageId\":"
                                + answer.getId()
                                + "}"
                );

                sendTokens(
                        emitter,
                        refusal
                );

                send(
                        emitter,
                        "done",
                        "{}"
                );

                emitter.complete();

                return;
            }

            /*
             * ========================================================
             * 6. 构造 RAG Context
             * ========================================================
             */
            String contextText =
                    buildContext(
                            contexts
                    );

            String allowedCitations =
                    buildAllowedCitations(
                            contexts.size()
                    );

            /*
             * ========================================================
             * 7. 严格证据约束 Prompt
             * ========================================================
             *
             * 这里重点解决：
             *
             * “资料没有提及”
             *
             * 被模型错误推理为：
             *
             * “不存在特殊规定”
             *
             * 的问题。
             */
            String systemPrompt =
        """
        你是 KnowFlow 企业知识库智能助手。

        你的任务：
        根据提供的企业知识库资料回答用户问题。

        回答规则：

        1. 优先回答用户问题中能够被知识库资料支持的内容。

        2. 如果知识库资料只包含部分答案：
           - 先回答已有信息；
           - 再说明当前资料未覆盖的部分。

        3. 不要因为资料不完整直接拒绝回答。

        4. 如果资料没有相关内容，请说明：
           “当前知识库暂无相关记录”。

        5. 严禁编造企业制度、审批流程、金额、时间等事实。

        6. 不允许使用模型自身知识补充企业内部规则。

        7. 回答采用结构化形式：
           
           结论：
           ...

           具体内容：
           ...

           说明：
           ...

        8. 每个重要事实后添加引用编号。

        9. 引用编号必须来自允许范围。

        当前允许引用：

        %s


        知识库资料：

        %s
        """
        .formatted(
                allowedCitations,
                contextText
        );

            /*
             * ========================================================
             * 8. 构造最终问题
             * ========================================================
             */
            String answerQuestion;

            if (
                    retrievalQuestion.equals(
                            request.question()
                    )
            ) {

                /*
                 * 第一轮或者问题本身已经完整。
                 */
                answerQuestion =
                        request.question();

            } else {

                /*
                 * 多轮追问。
                 *
                 * 同时告诉模型：
                 *
                 * 用户实际说了什么
                 * +
                 * Query Rewrite 后的完整语义。
                 */
                answerQuestion =
                        """
                        用户当前追问：

                        %s

                        根据历史对话，
                        检索系统将该问题理解并改写为：

                        %s

                        请依据提供的知识库资料，
                        回答用户当前真正想询问的问题。
                        """
                                .formatted(
                                        request.question(),
                                        retrievalQuestion
                                );
            }

            /*
             * ========================================================
             * 9. 调用真实 Chat Model
             * ========================================================
             */
            String rawAnswer =
                    chatModelProvider.answer(
                            systemPrompt,
                            answerQuestion
                    );

            /*
             * ========================================================
             * 10. Citation 编号兜底
             * ========================================================
             *
             * Prompt 只能降低模型产生非法引用的概率。
             *
             * 后端仍然必须进行最终校验。
             */
            String answerText =
                    normalizeCitationIndexes(
                            rawAnswer,
                            contexts.size()
                    );

            /*
             * ========================================================
             * 11. 保存 Assistant Message
             * ========================================================
             */
            MessageEntity answer =
                    saveAnswer(
                            user,
                            conversationId,
                            answerText
                    );

            /*
             * ========================================================
             * 12. 保存 Citation
             * ========================================================
             */
            saveCitations(
                    user.tenantId(),
                    answer.getId(),
                    contexts
            );

            /*
             * ========================================================
             * 13. SSE Meta
             * ========================================================
             */
            send(
                    emitter,
                    "meta",
                    "{\"conversationId\":"
                            + conversationId
                            + ",\"messageId\":"
                            + answer.getId()
                            + "}"
            );

            /*
             * ========================================================
             * 14. SSE Token
             * ========================================================
             */
            sendTokens(
                    emitter,
                    answerText
            );

            /*
             * ========================================================
             * 15. SSE Citation
             * ========================================================
             */
            for (
                    int i = 0;
                    i < contexts.size();
                    i++
            ) {

                ChunkSearchResult context =
                        contexts.get(i);

                String data =
                        String.format(
                                Locale.ROOT,

                                "{\"index\":%d,"
                                        + "\"documentId\":%d,"
                                        + "\"documentName\":%s,"
                                        + "\"pageNumber\":%s,"
                                        + "\"excerpt\":%s,"
                                        + "\"similarity\":%.4f}",

                                i + 1,

                                context.documentId(),

                                json(
                                        context.documentName()
                                ),

                                context.pageNumber() == null
                                        ? "null"
                                        : context.pageNumber(),

                                json(
                                        excerpt(
                                                context.content()
                                        )
                                ),

                                context.similarity()
                        );

                send(
                        emitter,
                        "citation",
                        data
                );
            }

            /*
             * ========================================================
             * 16. SSE Done
             * ========================================================
             */
            send(
                    emitter,
                    "done",
                    "{}"
            );

            emitter.complete();

        } catch (Exception ex) {

            log.error(
                    "Chat stream failed",
                    ex
            );

            try {

                send(
                        emitter,
                        "error",

                        "{\"message\":"
                                + json(
                                ex.getMessage()
                        )
                                + "}"
                );

            } catch (Exception ignored) {
            }

            emitter.completeWithError(
                    ex
            );
        }
    }

    /**
     * ============================================================
     * Query Rewrite
     * ============================================================
     *
     * 把多轮追问改写为完整、可独立检索的问题。
     */
    private String rewriteQuestion(
            UserPrincipal user,
            Long conversationId,
            String currentQuestion
    ) {

        List<MessageEntity> history =
                messageMapper.listByConversation(
                        conversationId,
                        user.tenantId()
                );

        /*
         * 第一轮没有历史消息，
         * 不调用模型改写。
         */
        if (
                history == null
                        || history.isEmpty()
        ) {

            return currentQuestion;
        }

        /*
         * 只获取最近若干条消息，
         * 控制 Token 开销。
         */
        int start =
                Math.max(
                        0,
                        history.size()
                                - REWRITE_HISTORY_LIMIT
                );

        List<MessageEntity> recentHistory =
                history.subList(
                        start,
                        history.size()
                );

        StringBuilder historyText =
                new StringBuilder();

        for (
                MessageEntity message :
                recentHistory
        ) {

            String role;

            if (
                    "USER".equals(
                            message.getRole()
                    )
            ) {

                role = "用户";

            } else {

                role = "助手";
            }

            historyText
                    .append(role)
                    .append("：")
                    .append(
                            message.getContent()
                    )
                    .append("\n");
        }

        String rewriteSystemPrompt =
                """
                你是企业知识库 RAG 系统中的查询改写器。

                你的任务不是回答用户问题。

                你的唯一任务是：

                根据历史对话，
                将用户当前问题改写成一个语义完整、
                可以脱离历史对话独立用于知识库检索的问题。

                规则：

                1. 只输出改写后的问题。

                2. 不回答问题。

                3. 不解释。

                4. 不输出：
                   “改写后的问题：”
                   “问题：”
                   等前缀。

                5. 不添加历史对话中不存在的信息。

                6. 如果当前问题本身已经完整，
                   保持其原意即可。

                7. 必须消除指代和上下文省略，例如：

                   “它”
                   “这个”
                   “那个”
                   “那高级员工呢”
                   “那5000元以下呢”
                   “那他呢”

                8. 必须保留：

                   数字
                   人员角色
                   制度名称
                   产品名称
                   时间
                   金额
                   条件

                   等重要检索信息。

                9. 改写后的问题应该简洁，
                   不要包含无关历史内容。
                """;

        String rewriteUserPrompt =
                """
                历史对话：

                %s

                当前问题：

                %s
                """
                        .formatted(
                                historyText,
                                currentQuestion
                        );

        try {

            String rewritten =
                    chatModelProvider.answer(
                            rewriteSystemPrompt,
                            rewriteUserPrompt
                    );

            if (
                    rewritten == null
                            || rewritten.isBlank()
            ) {

                return currentQuestion;
            }

            String cleaned =
                    cleanRewrittenQuestion(
                            rewritten,
                            currentQuestion
                    );

            log.info(
                    "Query rewrite result: '{}' -> '{}'",
                    currentQuestion,
                    cleaned
            );

            return cleaned;

        } catch (Exception ex) {

            /*
             * Query Rewrite 只是增强功能。
             *
             * 改写失败时，
             * 不能导致整个知识库问答不可用。
             */
            log.warn(
                    "Query rewrite failed, fallback to original question: {}",
                    ex.getMessage()
            );

            return currentQuestion;
        }
    }

    /**
     * 清理 Query Rewrite 输出。
     */
    private String cleanRewrittenQuestion(
            String rewritten,
            String fallback
    ) {

        if (
                rewritten == null
        ) {

            return fallback;
        }

        String result =
                rewritten.trim();

        /*
         * 删除模型偶尔生成的前缀。
         */
        result =
                result.replaceFirst(
                        "^(改写后的问题|改写结果|独立问题|问题)[:：]\\s*",
                        ""
                );

        /*
         * 删除英文引号。
         */
        if (
                result.startsWith("\"")
                        && result.endsWith("\"")
                        && result.length() > 1
        ) {

            result =
                    result.substring(
                            1,
                            result.length() - 1
                    );
        }

        /*
         * 删除中文引号。
         */
        if (
                result.startsWith("“")
                        && result.endsWith("”")
                        && result.length() > 1
        ) {

            result =
                    result.substring(
                            1,
                            result.length() - 1
                    );
        }

        result =
                result.trim();

        /*
         * 防止模型异常返回大段文字。
         */
        if (
                result.isBlank()
                        || result.length() > 300
        ) {

            return fallback;
        }

        return result;
    }

    /**
     * 保存 AI 回答。
     */
    private MessageEntity saveAnswer(
            UserPrincipal user,
            Long conversationId,
            String answerText
    ) {

        MessageEntity answer =
                new MessageEntity();

        answer.setTenantId(
                user.tenantId()
        );

        answer.setConversationId(
                conversationId
        );

        answer.setRole(
                "ASSISTANT"
        );

        answer.setContent(
                answerText
        );

        answer.setStatus(
                "COMPLETED"
        );

        messageMapper.insert(
                answer
        );

        ConversationEntity conversation =
                conversationMapper.selectById(
                        conversationId
                );

        if (
                conversation != null
        ) {

            conversation.setUpdatedAt(
                    OffsetDateTime.now()
            );

            conversationMapper.updateById(
                    conversation
            );
        }

        return answer;
    }

    /**
     * 保存引用来源。
     *
     * Citation Index 从 1 开始。
     */
    private void saveCitations(
            Long tenantId,
            Long messageId,
            List<ChunkSearchResult> contexts
    ) {

        for (
                int i = 0;
                i < contexts.size();
                i++
        ) {

            ChunkSearchResult context =
                    contexts.get(i);

            CitationEntity citation =
                    new CitationEntity();

            citation.setTenantId(
                    tenantId
            );

            citation.setMessageId(
                    messageId
            );

            citation.setDocumentId(
                    context.documentId()
            );

            citation.setChunkId(
                    context.chunkId()
            );

            citation.setPageNumber(
                    context.pageNumber()
            );

            citation.setCitationIndex(
                    i + 1
            );

            citation.setExcerpt(
                    excerpt(
                            context.content()
                    )
            );

            citation.setSimilarity(
                    context.similarity()
            );

            citationMapper.insert(
                    citation
            );
        }
    }

    /**
     * 确认会话属于当前租户和当前用户。
     */
    private ConversationEntity requireConversation(
            Long conversationId,
            UserPrincipal user
    ) {

        ConversationEntity conversation =
                conversationMapper.findMine(
                        conversationId,
                        user.tenantId(),
                        user.userId()
                );

        if (
                conversation == null
        ) {

            throw BusinessException.notFound(
                    "对话不存在"
            );
        }

        return conversation;
    }

    /**
     * 构造传给模型的知识库 Context。
     *
     * 示例：
     *
     * [1] 文档：员工报销制度.md
     * ...
     *
     * [2] 文档：差旅制度.md
     * ...
     */
    private String buildContext(
            List<ChunkSearchResult> contexts
    ) {

        StringBuilder builder =
                new StringBuilder();

        for (
                int i = 0;
                i < contexts.size();
                i++
        ) {

            ChunkSearchResult context =
                    contexts.get(i);

            builder
                    .append("[")
                    .append(
                            i + 1
                    )
                    .append("] 文档：")
                    .append(
                            context.documentName()
                    );

            if (
                    context.pageNumber()
                            != null
            ) {

                builder
                        .append("，第")
                        .append(
                                context.pageNumber()
                        )
                        .append("页");
            }

            builder
                    .append("\n")
                    .append(
                            context.content()
                    )
                    .append(
                            "\n\n"
                    );
        }

        return builder.toString();
    }

    /**
     * 构造当前允许的 Citation 编号。
     *
     * 例如：
     *
     * [1]
     *
     * 或：
     *
     * [1]、[2]、[3]
     */
    private String buildAllowedCitations(
            int count
    ) {

        StringBuilder builder =
                new StringBuilder();

        for (
                int i = 1;
                i <= count;
                i++
        ) {

            if (
                    i > 1
            ) {

                builder.append(
                        "、"
                );
            }

            builder
                    .append("[")
                    .append(i)
                    .append("]");
        }

        return builder.toString();
    }

    /**
     * Citation 编号后端兜底校验。
     */
    private String normalizeCitationIndexes(
            String answer,
            int citationCount
    ) {

        if (
                answer == null
                        || answer.isBlank()
                        || citationCount <= 0
        ) {

            return answer;
        }

        Pattern pattern =
                Pattern.compile(
                        "\\[(\\d+)]"
                );

        Matcher matcher =
                pattern.matcher(
                        answer
                );

        StringBuffer result =
                new StringBuffer();

        while (
                matcher.find()
        ) {

            int index;

            try {

                index =
                        Integer.parseInt(
                                matcher.group(
                                        1
                                )
                        );

            } catch (
                    NumberFormatException ex
            ) {

                matcher.appendReplacement(
                        result,
                        ""
                );

                continue;
            }

            String replacement;

            /*
             * 合法引用：
             * 原样保留。
             */
            if (
                    index >= 1
                            && index <= citationCount
            ) {

                replacement =
                        "["
                                + index
                                + "]";

            /*
             * 当前只有一个来源时，
             * 如果模型错误生成 [2]、[3]，
             * 明确修正为 [1]。
             */
            } else if (
                    citationCount == 1
            ) {

                replacement =
                        "[1]";

            /*
             * 多来源时，
             * 无法判断模型错误编号实际想引用谁，
             * 因此删除非法引用。
             */
            } else {

                replacement =
                        "";
            }

            matcher.appendReplacement(
                    result,

                    Matcher.quoteReplacement(
                            replacement
                    )
            );
        }

        matcher.appendTail(
                result
        );

        return result.toString();
    }

    /**
     * 将完整模型回答切成小块，
     * 模拟 SSE Token Streaming。
     */
    private void sendTokens(
            SseEmitter emitter,
            String text
    ) throws IOException, InterruptedException {

        int chunkSize =
                12;

        for (
                int i = 0;
                i < text.length();
                i += chunkSize
        ) {

            String part =
                    text.substring(
                            i,

                            Math.min(
                                    text.length(),
                                    i + chunkSize
                            )
                    );

            send(
                    emitter,
                    "token",

                    "{\"content\":"
                            + json(part)
                            + "}"
            );

            Thread.sleep(
                    18
            );
        }
    }

    /**
     * 发送 SSE Event。
     */
    private void send(
            SseEmitter emitter,
            String event,
            String data
    ) throws IOException {

        emitter.send(
                SseEmitter
                        .event()
                        .name(
                                event
                        )
                        .data(
                                data
                        )
        );
    }

    /**
     * 引用摘要。
     */
    private String excerpt(
            String content
    ) {

        if (
                content == null
        ) {

            return "";
        }

        return content.length() <= 220
                ? content
                : content.substring(
                0,
                220
        ) + "…";
    }

    /**
     * 根据第一条问题生成对话标题。
     */
    private String shortTitle(
            String question
    ) {

        if (
                question == null
                        || question.isBlank()
        ) {

            return "新对话";
        }

        String value =
                question.trim();

        return value.length() <= 24
                ? value
                : value.substring(
                0,
                24
        ) + "…";
    }

    /**
     * 简单 JSON String 转义。
     */
    private String json(
            String value
    ) {

        if (
                value == null
        ) {

            return "null";
        }

        return "\""
                + value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                + "\"";
    }
}