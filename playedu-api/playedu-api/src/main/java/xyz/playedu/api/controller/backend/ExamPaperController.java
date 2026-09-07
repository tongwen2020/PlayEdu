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
package xyz.playedu.api.controller.backend;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.playedu.common.annotation.BackendPermission;
import xyz.playedu.common.constant.BPermissionConstant;
import xyz.playedu.common.types.JsonResponse;
import xyz.playedu.exam.ExamPaperService;
import xyz.playedu.exam.ExamPaperTypes.*;
import xyz.playedu.exam.QuestionBankService;
import xyz.playedu.exam.QuestionBankTypes.Query;

/** All paper-library operations use POST, consistent with the question-bank API. */
@RestController
@RequestMapping("/backend/v1/exam-paper")
public class ExamPaperController {
    private final ExamPaperService service;
    private final QuestionBankService questions;

    public ExamPaperController(ExamPaperService service, QuestionBankService questions) {
        this.service = service;
        this.questions = questions;
    }

    @PostMapping("/categories/list")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_VIEW)
    public JsonResponse categories() {
        return JsonResponse.data(service.categories());
    }

    @PostMapping("/categories/create")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse createCategory(@Valid @RequestBody CategoryInput input) {
        return JsonResponse.data(service.createCategory(input));
    }

    @PostMapping("/categories/rename")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse renameCategory(@Valid @RequestBody CategoryRename input) {
        service.renameCategory(input);
        return JsonResponse.success();
    }

    @PostMapping("/categories/delete")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse deleteCategory(@Valid @RequestBody IdInput input) {
        service.deleteCategory(input.id());
        return JsonResponse.success();
    }

    @PostMapping("/papers/list")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_VIEW)
    public JsonResponse papers(@Valid @RequestBody PaperQuery input) {
        return JsonResponse.data(service.list(input));
    }

    @PostMapping("/papers/detail")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_VIEW)
    public JsonResponse detail(@Valid @RequestBody VersionInput input) {
        return JsonResponse.data(service.detail(input));
    }

    @PostMapping("/papers/save")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse save(@Valid @RequestBody PaperInput input) {
        return JsonResponse.data(service.save(input));
    }

    @PostMapping("/papers/delete-draft")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse deleteDraft(@Valid @RequestBody IdInput input) {
        service.deleteDraft(input.id());
        return JsonResponse.success();
    }

    @PostMapping("/papers/validate")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse validate(@Valid @RequestBody IdInput input) {
        return JsonResponse.data(service.validate(input.id()));
    }

    @PostMapping("/papers/publish")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_PUBLISH)
    public JsonResponse publish(@Valid @RequestBody RevisionInput input) {
        return JsonResponse.data(service.publish(input));
    }

    @PostMapping("/papers/status")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_PUBLISH)
    public JsonResponse status(@Valid @RequestBody StatusInput input) {
        return JsonResponse.data(service.changeStatus(input));
    }

    @PostMapping("/papers/copy")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse copy(@Valid @RequestBody CopyInput input) {
        return JsonResponse.data(service.copy(input));
    }

    @PostMapping("/papers/versions")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_VIEW)
    public JsonResponse versions(@Valid @RequestBody IdInput input) {
        return JsonResponse.data(service.versions(input.id()));
    }

    @PostMapping("/papers/export")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EXPORT)
    public JsonResponse export(@Valid @RequestBody VersionInput input) {
        return JsonResponse.data(service.export(input));
    }

    @PostMapping("/papers/import")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse importPapers(@Valid @RequestBody ImportInput input) {
        return JsonResponse.data(service.importPapers(input));
    }

    @PostMapping("/questions/search")
    @BackendPermission(slug = BPermissionConstant.EXAM_PAPER_EDIT)
    public JsonResponse searchQuestions(@Valid @RequestBody Query input) {
        return JsonResponse.data(questions.questions(input, false));
    }
}
