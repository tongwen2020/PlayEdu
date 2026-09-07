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

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** Question bank v1: typed content, answers and immutable grading rules. */
public final class QuestionBankTypes {
    private QuestionBankTypes() {}

    public record BankInput(
            Long id,
            Integer revision,
            @NotBlank @Size(max = 100) String name,
            @NotNull @Size(max = 1000) String description,
            @NotNull @Pattern(regexp = "enabled|disabled") String status,
            boolean practiceEnabled) {}

    public record IdInput(@NotNull @Positive Long id) {}

    public record BankIdInput(@NotNull @Positive Long bankId) {}

    public record CategoryInput(
            @NotNull @Positive Long bankId,
            @NotNull @PositiveOrZero Long parentId,
            @NotBlank @Size(max = 100) String name) {}

    public record CategoryRename(
            @NotNull @Positive Long id, @NotBlank @Size(max = 100) String name) {}

    public record Option(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String id,
            @NotBlank @Size(max = 2000) String text) {}

    public record Answer(
            @Size(max = 8) List<@NotBlank String> optionIds,
            Boolean value,
            @Size(max = 10000) String text) {}

    public record GradingRule(@NotNull @Pattern(regexp = "exact_match|manual") String strategy) {}

    public record QuestionInput(
            Long id,
            Integer expectedVersion,
            @NotNull @Positive Long bankId,
            Long categoryId,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String code,
            @NotNull @Pattern(regexp = "single_choice|multiple_choice|true_false|short_answer")
                    String type,
            @NotNull @Pattern(regexp = "easy|medium|hard") String difficulty,
            @NotBlank @Size(max = 10000) String stem,
            @NotNull @Size(max = 8) List<@NotNull @Valid Option> options,
            @NotNull @Valid Answer standardAnswer,
            @NotNull @Valid GradingRule gradingRule,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                    BigDecimal suggestedScore,
            @NotNull @Size(max = 10000) String analysis,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
            @NotNull @Pattern(regexp = "draft|enabled|disabled|archived") String status) {}

    public record Query(
            Long bankId,
            Long categoryId,
            @Size(max = 100) String keyword,
            @Size(max = 30) String type,
            @Size(max = 20) String difficulty,
            @Size(max = 20) String status,
            @Size(max = 40) String tag,
            @Min(1) @Max(100000) int page,
            @Min(1) @Max(100) int size) {}

    public record VersionInput(@NotNull @Positive Long id, @Positive Integer version) {}

    public record StatusInput(
            @NotNull @Positive Long id,
            @NotNull @Positive Integer expectedVersion,
            @NotNull @Pattern(regexp = "draft|enabled|disabled|archived") String status) {}

    public record ImportInput(
            @NotNull @Positive Long bankId,
            @NotEmpty @Size(max = 1000) List<@NotNull @Valid QuestionInput> questions) {}

    public record PracticeInput(
            @NotNull @Positive Long questionId,
            @NotNull @Positive Integer version,
            @NotNull @Valid Answer answer,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{8,64}") String requestKey) {}

    public record Grade(BigDecimal score, BigDecimal maxScore, String result) {}
}
