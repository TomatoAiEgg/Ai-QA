package cn.net.susan.ai.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档切片器配置
 * 用于配置 RAG 中文档切片的策略和参数
 *
 * @author TomatoEgg
 * @date 2026/3/15
 */
@Configuration
@ConfigurationProperties(prefix = "ai.service.document.splitter")
public class DocumentSplitterConfiguration {

    /**
     * 每个文档块的目标大小（token 数）
     * 默认 512，较大的值会保留更多上下文，但可能降低检索精度
     */
    private int defaultChunkSize = 512;

    /**
     * 每个文档块的最小字符数
     * 用于避免产生过小的片段
     */
    private int minChunkSizeChars = 128;

    /**
     * 被嵌入的块的最小长度（token 数）
     * 小于此值的片段不会被嵌入
     */
    private int minChunkLengthToEmbed = 5;

    /**
     * 从文本生成的最大块数
     * 限制单个文档生成的最大片段数量
     */
    private int maxNumChunks = 10000;

    /**
     * 是否在块中保留分隔符（如换行符）
     * true: 保留分隔符，保持原文格式
     * false: 移除分隔符
     */
    private boolean keepSeparator = true;

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
                defaultChunkSize,
                minChunkSizeChars,
                minChunkLengthToEmbed,
                maxNumChunks,
                keepSeparator
        );
    }

    // Getters and Setters
    public int getDefaultChunkSize() {
        return defaultChunkSize;
    }

    public void setDefaultChunkSize(int defaultChunkSize) {
        this.defaultChunkSize = defaultChunkSize;
    }

    public int getMinChunkSizeChars() {
        return minChunkSizeChars;
    }

    public void setMinChunkSizeChars(int minChunkSizeChars) {
        this.minChunkSizeChars = minChunkSizeChars;
    }

    public int getMinChunkLengthToEmbed() {
        return minChunkLengthToEmbed;
    }

    public void setMinChunkLengthToEmbed(int minChunkLengthToEmbed) {
        this.minChunkLengthToEmbed = minChunkLengthToEmbed;
    }

    public int getMaxNumChunks() {
        return maxNumChunks;
    }

    public void setMaxNumChunks(int maxNumChunks) {
        this.maxNumChunks = maxNumChunks;
    }

    public boolean isKeepSeparator() {
        return keepSeparator;
    }

    public void setKeepSeparator(boolean keepSeparator) {
        this.keepSeparator = keepSeparator;
    }

    @Override
    public String toString() {
        return "DocumentSplitterConfiguration{" +
                "defaultChunkSize=" + defaultChunkSize +
                ", minChunkSizeChars=" + minChunkSizeChars +
                ", minChunkLengthToEmbed=" + minChunkLengthToEmbed +
                ", maxNumChunks=" + maxNumChunks +
                ", keepSeparator=" + keepSeparator +
                '}';
    }
}
