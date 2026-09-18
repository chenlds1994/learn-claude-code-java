package com.learnclaudecode.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 记忆记录，持久化到 .memory/ 目录。
 * 每条记忆有唯一 ID、内容、分类和时间戳。
 *
 * 分类只有三种，用来教会读者“记忆也要分门别类”：
 * - fact：客观事实（例如项目使用的语言版本、约定）；
 * - preference：用户偏好（例如喜欢中文注释、倾向某种风格）；
 * - procedure：可复用的操作步骤（例如某个命令的完整流程）。
 *
 * 加 {@link JsonIgnoreProperties} 是为了向前兼容：
 * 未来给这个模型新增字段时，旧会话写下的 JSON 文件仍然能被正常反序列化，
 * 不会因为“多了一个未知字段”而整条记忆读取失败。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryRecord {
    /**
     * 记忆唯一 ID，使用 UUID 字符串，同时作为落盘文件名（&lt;id&gt;.json）。
     */
    public String id;

    /**
     * 记忆正文内容。
     */
    public String content;

    /**
     * 记忆分类，取值为 fact、preference、procedure 之一。
     */
    public String category;

    /**
     * 创建时间，Unix 毫秒时间戳。
     */
    public long createdAt;

    /**
     * 最近更新时间，Unix 毫秒时间戳。整合（consolidate）时会用它挑出“最新的一条”。
     */
    public long updatedAt;

    /**
     * 供 Jackson 反序列化使用的无参构造函数。
     */
    public MemoryRecord() {
    }

    /**
     * 创建一条新记忆，并自动打上创建/更新时间戳。
     *
     * @param id 记忆唯一 ID
     * @param content 记忆内容
     * @param category 记忆分类
     */
    public MemoryRecord(String id, String content, String category) {
        this.id = id;
        this.content = content;
        this.category = category;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }
}
