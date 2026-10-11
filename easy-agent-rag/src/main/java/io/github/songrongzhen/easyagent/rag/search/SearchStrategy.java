package io.github.songrongzhen.easyagent.rag.search;

import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;

import java.util.List;

/**
 * 搜索策略接口
 */
@FunctionalInterface
public interface SearchStrategy {

    /**
     * 执行语义搜索
     *
     * @param query     用户查询文本
     * @param documents 待搜索的文档列表
     * @param topK      返回的最相关文档数量
     * @return 按相关度排序的文档列表
     */
    List<DocumentChunk> search(String query, List<DocumentChunk> documents, int topK);

    /** Validate before fallback or external model calls so invalid input is not swallowed. */
    static boolean hasSearchInput(String query, int topK) {
        if (topK < 0) {
            throw new IllegalArgumentException("topK must be greater than or equal to 0");
        }
        return topK > 0 && query != null && !query.isBlank();
    }

    /**
     * 文档入库前的准备动作，例如生成文档向量。
     *
     * @param documents 待入库文档
     * @return 准备后的文档
     */
    default List<DocumentChunk> prepareDocuments(List<DocumentChunk> documents) {
        return documents;
    }

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
