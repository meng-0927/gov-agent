package com.example.govagent.executor;

import com.example.govagent.exception.AgentException;
import com.example.govagent.model.AgentContext;
import com.example.govagent.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责步骤之间的数据传递。
 * 例如 Planner 输出 ${step1.output} 或 ${step1.data.documents}，这里会在执行前替换成 step1 的真实结果对象。
 */
@Component
public class ArgumentResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "^\\$\\{([a-zA-Z0-9_-]+)\\.(output|data)(?:\\.([a-zA-Z0-9_-]+))?}$");

    public Map<String, Object> resolve(Map<String, Object> arguments, AgentContext context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        arguments.forEach((key, value) -> resolved.put(key, resolveValue(value, context)));
        return resolved;
    }

    private Object resolveValue(Object value, AgentContext context) {
        if (value instanceof String s) {
            // 只有整个字符串完全匹配占位符时才解析，普通文本原样保留。
            Matcher matcher = PLACEHOLDER.matcher(s);
            if (!matcher.matches()) {
                return s;
            }
            String stepId = matcher.group(1);
            String root = matcher.group(2);
            String child = matcher.group(3);
            // 引用前序步骤时，必须保证该步骤已经执行且成功。
            ToolResult result = context.getStepResult(stepId);
            if (result == null || !result.success()) {
                throw new AgentException("Cannot resolve placeholder from unfinished step: " + stepId);
            }
            if ("output".equals(root)) {
                return result.output();
            }
            if (child == null) {
                return result.data();
            }
            if (!result.data().containsKey(child)) {
                throw new AgentException("Step " + stepId + " data has no key: " + child);
            }
            return result.data().get(child);
        }

        // 参数可能是嵌套 Map/List，因此递归解析其中的占位符。
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((k, v) -> nested.put(String.valueOf(k), resolveValue(v, context)));
            return nested;
        }

        if (value instanceof List<?> list) {
            List<Object> nested = new ArrayList<>();
            for (Object item : list) {
                nested.add(resolveValue(item, context));
            }
            return nested;
        }

        return value;
    }
}
