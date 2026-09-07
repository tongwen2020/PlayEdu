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

/** Inputs for fixed paper composition and immutable publication. */
public final class ExamPaperTypes {
    private ExamPaperTypes() {}

    public record IdInput(@NotNull @Positive Long id) {}

    public record CategoryInput(
            @NotNull @PositiveOrZero Long parentId, @NotBlank @Size(max = 100) String name) {}

    public record CategoryRename(
            @NotNull @Positive Long id, @NotBlank @Size(max = 100) String name) {}

    public record PaperItemInput(
            @NotNull @Positive Long questionId,
            @NotNull @Positive Integer questionVersion,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                    BigDecimal score,
            @Min(0) int position) {}

    public record PaperSectionInput(
            @NotBlank @Size(max = 100) String title,
            @NotNull @Size(max = 1000) String description,
            @Min(0) int position,
            boolean shuffleQuestions,
            @NotNull @Size(max = 500) List<@NotNull @Valid PaperItemInput> items) {}

    public record PaperInput(
            Long id,
            Integer revision,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull @Size(max = 1000) String description,
            @Positive Long categoryId,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
            @NotNull @Size(max = 50) List<@NotNull @Valid PaperSectionInput> sections) {}

    public record PaperQuery(
            @Positive Long categoryId,
            @Size(max = 100) String keyword,
            @Size(max = 20) String status,
            @Size(max = 40) String tag,
            @Min(1) @Max(100000) int page,
            @Min(1) @Max(100) int size) {}

    public record VersionInput(@NotNull @Positive Long id, @Positive Integer version) {}

    public record RevisionInput(
            @NotNull @Positive Long id, @NotNull @Positive Integer expectedRevision) {}

    public record StatusInput(
            @NotNull @Positive Long id,
            @NotNull @Positive Integer expectedRevision,
            @NotNull @Pattern(regexp = "published|disabled|archived") String status) {}

    public record CopyInput(
            @NotNull @Positive Long sourceId,
            @Positive Integer version,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String code,
            @NotBlank @Size(max = 100) String name) {}

    public record ImportInput(@NotEmpty @Size(max = 100) List<@NotNull @Valid PaperInput> papers) {}
}
