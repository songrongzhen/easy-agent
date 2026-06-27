package io.github.songrongzhen.easyagent.rag.store;

import java.util.List;

/**
 * 向量存储提供者接口
 */
public interface VectorStoreProvider {

    /**
     * 添加文档到向量存储
     *
     * @param chunks 文档块列表
     */
    void add(List<DocumentChunk> chunks);

    /**
     * 搜索相关文档
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 相关文档列表
     */
    List<DocumentChunk> search(String query, int topK);

    /**
     * 删除文档
     *
     * @param ids 文档ID列表
     */
    void delete(List<String> ids);

    /**
     * 删除指定来源的文档块。
     *
     * @param source 文档来源
     */
    void deleteBySource(String source);

    /**
     * 删除指定文档ID的文档块。
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(String documentId);

    /**
     * 删除所有文档
     */
    void deleteAll();

    /**
     * 检查存储是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取存储名称
     *
     * @return 存储名称
     */
    String getName();

    /**
     * 获取文档数量
     *
     * @return 文档数量
     */
    int count();

    /**
     * 获取所有文档
     *
     * @return 所有文档列表
     */
    List<DocumentChunk> getAll();
}
