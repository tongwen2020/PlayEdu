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
import xyz.playedu.exam.QuestionBankService;
import xyz.playedu.exam.QuestionBankTypes.*;

/** All question-bank REST operations use POST, including queries and exports. */
@RestController
@RequestMapping("/backend/v1/question-bank")
public class QuestionBankController {
    private final QuestionBankService service;

    public QuestionBankController(QuestionBankService service) {
        this.service = service;
    }

    @PostMapping("/banks/list")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_VIEW)
    public JsonResponse banks() {
        return JsonResponse.data(service.banks());
    }

    @PostMapping("/banks/save")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse saveBank(@Valid @RequestBody BankInput input) {
        return JsonResponse.data(service.saveBank(input));
    }

    @PostMapping("/banks/delete")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse deleteBank(@Valid @RequestBody IdInput input) {
        service.deleteBank(input.id());
        return JsonResponse.success();
    }

    @PostMapping("/categories/list")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_VIEW)
    public JsonResponse categories(@Valid @RequestBody BankIdInput input) {
        return JsonResponse.data(service.categories(input.bankId()));
    }

    @PostMapping("/categories/create")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse createCategory(@Valid @RequestBody CategoryInput input) {
        return JsonResponse.data(service.createCategory(input));
    }

    @PostMapping("/categories/rename")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse renameCategory(@Valid @RequestBody CategoryRename input) {
        service.renameCategory(input);
        return JsonResponse.success();
    }

    @PostMapping("/categories/delete")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse deleteCategory(@Valid @RequestBody IdInput input) {
        service.deleteCategory(input.id());
        return JsonResponse.success();
    }

    @PostMapping("/questions/list")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_VIEW)
    public JsonResponse questions(@Valid @RequestBody Query input) {
        return JsonResponse.data(service.questions(input, false));
    }

    @PostMapping("/questions/detail")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_VIEW)
    public JsonResponse detail(@Valid @RequestBody VersionInput input) {
        return JsonResponse.data(service.detail(input));
    }

    @PostMapping("/questions/save")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse save(@Valid @RequestBody QuestionInput input) {
        return JsonResponse.data(service.saveQuestion(input));
    }

    @PostMapping("/questions/delete-draft")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse deleteDraft(@Valid @RequestBody IdInput input) {
        service.deleteDraft(input.id());
        return JsonResponse.success();
    }

    @PostMapping("/questions/status")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse status(@Valid @RequestBody StatusInput input) {
        return JsonResponse.data(service.changeStatus(input));
    }

    @PostMapping("/questions/versions")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_VIEW)
    public JsonResponse versions(@Valid @RequestBody IdInput input) {
        return JsonResponse.data(service.versions(input.id()));
    }

    @PostMapping("/questions/import")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EDIT)
    public JsonResponse importQuestions(@Valid @RequestBody ImportInput input) {
        return JsonResponse.data(service.importQuestions(input));
    }

    @PostMapping("/questions/export")
    @BackendPermission(slug = BPermissionConstant.QUESTION_BANK_EXPORT)
    public JsonResponse exportQuestions(@Valid @RequestBody BankIdInput input) {
        return JsonResponse.data(service.exportQuestions(input.bankId()));
    }
}
