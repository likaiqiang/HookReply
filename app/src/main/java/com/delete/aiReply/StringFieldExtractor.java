package com.delete.aiReply;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Set;
import java.util.Stack;

public class StringFieldExtractor {
    /**
     * 从对象的toString()输出中提取指定字段并转换为JSONObject
     * @param toStringOutput 对象的toString()输出字符串
     * @param fieldsToExtract 需要提取的字段集合
     * @return 包含提取字段的JSONObject
     * @throws JSONException 当JSON解析出错时
     */
    public static JSONObject extractFieldsAsJson(String toStringOutput, Set<String> fieldsToExtract) throws JSONException {
        JSONObject result = new JSONObject();

        if (toStringOutput == null) {
            return result;
        }

        // 检查并提取括号内的内容
        int openParenIndex = toStringOutput.indexOf("(");
        int closeParenIndex = findMatchingClosingParenthesis(toStringOutput, openParenIndex);

        if (openParenIndex == -1 || closeParenIndex == -1) {
            return result;
        }

        String body = toStringOutput.substring(openParenIndex + 1, closeParenIndex);

        // 解析字段
        try {
            parseFields(body, fieldsToExtract, result);
        } catch (Exception e) {
            throw new JSONException("Error parsing fields: " + e.getMessage());
        }

        return result;
    }

    /**
     * 查找匹配的右括号位置
     */
    private static int findMatchingClosingParenthesis(String str, int openIndex) {
        if (openIndex < 0 || openIndex >= str.length()) {
            return -1;
        }

        Stack<Character> stack = new Stack<>();
        for (int i = openIndex; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') {
                stack.push(c);
            } else if (c == ')') {
                if (!stack.isEmpty()) {
                    stack.pop();
                    if (stack.isEmpty()) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 解析字段并添加到JSONObject中
     */
    private static void parseFields(String body, Set<String> fieldsToExtract, JSONObject result) throws JSONException {
        int index = 0;
        String currentKey = null;
        StringBuilder valueBuilder = new StringBuilder();
        boolean insideQuotes = false;
        boolean insideBraces = false;
        int braceCount = 0;

        while (index < body.length()) {
            char c = body.charAt(index);

            // 处理键
            if (currentKey == null && !insideQuotes && !insideBraces && c == '=') {
                currentKey = valueBuilder.toString().trim();
                valueBuilder = new StringBuilder();
                index++;
                continue;
            }

            // 处理引号内的内容
            if (c == '"' && (index == 0 || body.charAt(index - 1) != '\\')) {
                insideQuotes = !insideQuotes;
            }

            // 处理花括号内的内容
            if (!insideQuotes) {
                if (c == '{') {
                    insideBraces = true;
                    braceCount++;
                } else if (c == '}' && insideBraces) {
                    braceCount--;
                    if (braceCount == 0) {
                        insideBraces = false;
                    }
                }
            }

            // 如果遇到逗号且不在引号内也不在花括号内，说明一个字段结束
            if (c == ',' && !insideQuotes && !insideBraces) {
                if (currentKey != null && fieldsToExtract.contains(currentKey)) {
                    addValueToJson(currentKey, valueBuilder.toString().trim(), result);
                }
                currentKey = null;
                valueBuilder = new StringBuilder();
            } else {
                valueBuilder.append(c);
            }

            index++;
        }

        // 处理最后一个字段
        if (currentKey != null && fieldsToExtract.contains(currentKey)) {
            addValueToJson(currentKey, valueBuilder.toString().trim(), result);
        }
    }

    /**
     * 根据值的类型将其添加到JSONObject中
     */
    private static void addValueToJson(String key, String value, JSONObject result) throws JSONException {
        // 移除字符串的引号
        if (value.startsWith("\"") && value.endsWith("\"")) {
            String unquoted = value.substring(1, value.length() - 1);
            result.put(key, unquoted);
            return;
        }

        // 处理null
        if ("null".equals(value)) {
            result.put(key, JSONObject.NULL);
            return;
        }

        // 处理布尔值
        if ("true".equals(value) || "false".equals(value)) {
            result.put(key, Boolean.parseBoolean(value));
            return;
        }

        // 处理数字
        if (value.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
            try {
                if (value.contains(".") || value.contains("e") || value.contains("E")) {
                    result.put(key, Double.parseDouble(value));
                } else {
                    // 尝试使用Integer，但如果值太大则使用Long
                    try {
                        result.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        result.put(key, Long.parseLong(value));
                    }
                }
                return;
            } catch (NumberFormatException e) {
                // 如果解析数字失败，作为字符串处理
            }
        }

        // 处理嵌套的JSON对象
        if (value.startsWith("{") && value.endsWith("}")) {
            try {
                result.put(key, new JSONObject(value));
                return;
            } catch (JSONException e) {
                // 如果解析JSON失败，作为字符串处理
            }
        }

        // 默认作为字符串处理
        result.put(key, value);
    }
}