/*
 * Copyright (C) 2023 杭州白书科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.playedu.exam;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import xyz.playedu.common.exception.ServiceException;
import xyz.playedu.exam.QuestionBankTypes.*;

/** All grading is server-side; false is an answer, null is unanswered. */
@Component
public class QuestionGrader {
    private void require(boolean condition, String message) {
        if (!condition) throw new ServiceException(message);
    }

    public void validate(QuestionInput q) {
        boolean choice = q.type().equals("single_choice") || q.type().equals("multiple_choice");
        Set<String> ids = new HashSet<>();
        for (Option o : q.options()) require(ids.add(o.id()), "选项 ID 不能重复");
        Answer a = q.standardAnswer();
        if (choice) {
            require(q.options().size() >= (q.type().equals("single_choice") ? 2 : 3), "选项数量不足");
            require(a.optionIds() != null && !a.optionIds().isEmpty(), "请选择正确选项");
            require(new HashSet<>(a.optionIds()).size() == a.optionIds().size(), "答案选项不能重复");
            require(ids.containsAll(a.optionIds()), "标准答案包含不存在的选项");
            require(
                    q.type().equals("single_choice")
                            ? a.optionIds().size() == 1
                            : a.optionIds().size() >= 2,
                    "正确选项数量与题型不符");
            require(a.value() == null && a.text() == null, "选择题仅允许选项答案");
        } else {
            require(q.options().isEmpty(), "该题型不允许自定义选项");
            require(a.optionIds() == null || a.optionIds().isEmpty(), "该题型不允许选项答案");
            if (q.type().equals("true_false")) {
                require(a.value() != null && a.text() == null, "判断题必须设置正确或错误");
            } else {
                require(
                        a.value() == null && a.text() != null && !a.text().isBlank(),
                        "简答题必须填写参考答案和评分要点");
            }
        }
        require(
                q.gradingRule()
                        .strategy()
                        .equals(q.type().equals("short_answer") ? "manual" : "exact_match"),
                "评分规则与题型不符");
        require(new HashSet<>(q.tags()).size() == q.tags().size(), "标签不能重复");
    }

    public Grade grade(QuestionInput q, Answer a) {
        require(!q.type().equals("short_answer"), "简答题需人工阅卷，暂不开放自动练习");
        boolean correct;
        if (q.type().equals("true_false")) {
            require(
                    (a.optionIds() == null || a.optionIds().isEmpty()) && a.text() == null,
                    "判断题答案格式错误");
            correct = a.value() != null && a.value().equals(q.standardAnswer().value());
        } else {
            require(a.value() == null && a.text() == null, "选择题答案格式错误");
            List<String> selected = a.optionIds() == null ? List.of() : a.optionIds();
            Set<String> actual = new HashSet<>(selected);
            require(actual.size() == selected.size(), "答案选项不能重复");
            require(
                    q.options().stream().map(Option::id).toList().containsAll(actual),
                    "答案包含不存在的选项");
            require(!q.type().equals("single_choice") || actual.size() <= 1, "单选题只能选择一个选项");
            correct = actual.equals(new HashSet<>(q.standardAnswer().optionIds()));
        }
        return new Grade(
                correct ? q.suggestedScore() : BigDecimal.ZERO.setScale(2),
                q.suggestedScore(),
                correct ? "correct" : "incorrect");
    }
}
