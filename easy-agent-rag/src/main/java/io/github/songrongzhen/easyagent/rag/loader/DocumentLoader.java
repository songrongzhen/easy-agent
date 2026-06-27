package io.github.songrongzhen.easyagent.rag.loader;

import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface DocumentLoader {

    /**
     * 判断是否支持该文件。
     */
    boolean supports(String filename);

    /**
     * 从输入流加载文档块。
     */
    List<DocumentChunk> load(String documentId, String filename, InputStream inputStream) throws IOException;
}
