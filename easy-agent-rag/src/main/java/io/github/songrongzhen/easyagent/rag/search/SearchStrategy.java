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

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
