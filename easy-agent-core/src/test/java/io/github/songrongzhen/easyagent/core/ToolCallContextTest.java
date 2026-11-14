package io.github.songrongzhen.easyagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 工具上下文 JSON 快照的返回类型、深度只读性和输入边界回归测试。 */
class ToolCallContextTest {

    @Test
    void collectionSnapshotReturnsAnExplicitJsonArray() {
        Set<String> input = new LinkedHashSet<>(List.of("first", "second"));
        Object snapshot = ToolCallContext.snapshotValue(input);
        List<?> values = assertInstanceOf(List.class, snapshot);
        input.clear();
        assertEquals(List.of("first", "second"), values);
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void mapSnapshotCopiesNestedMapsCollectionsAndNulls() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("nullable", null);
        List<String> list = new ArrayList<>(List.of("original"));
        Map<String, Object> input = new HashMap<>();
        input.put("nested", nested);
        input.put("list", list);
        input.put("set", Set.of("value"));
        Map<String, Object> snapshot = ToolCallContext.snapshot(input);
        nested.clear();
        list.clear();
        input.clear();
        assertEquals(List.of("original"), snapshot.get("list"));
        assertEquals(List.of("value"), snapshot.get("set"));
        Map<?, ?> nestedSnapshot = assertInstanceOf(Map.class, snapshot.get("nested"));
        assertEquals(1, nestedSnapshot.size());
        assertNull(nestedSnapshot.get("nullable"));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, nestedSnapshot::clear);
        assertThrows(
            UnsupportedOperationException.class,
            () -> assertInstanceOf(List.class, snapshot.get("list")).clear());
    }

    @Test
    void snapshotRejectsUnsupportedValuesAndPreservesNull() {
        assertNull(ToolCallContext.snapshot(null));
        assertNull(ToolCallContext.snapshotValue(null));
        assertEquals("text", ToolCallContext.snapshotValue("text"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ToolCallContext.snapshotValue(Map.of(1, "invalid key")));
        assertThrows(
            IllegalArgumentException.class,
            () -> ToolCallContext.snapshot(Map.of("active", new Object())));
    }
}
