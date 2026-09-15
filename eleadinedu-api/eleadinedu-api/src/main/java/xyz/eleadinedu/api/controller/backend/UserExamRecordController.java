/*
 * Copyright (C) 南京意领信息科技有限公司
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
package xyz.eleadinedu.api.controller.backend;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.eleadinedu.common.annotation.BackendPermission;
import xyz.eleadinedu.common.annotation.Log;
import xyz.eleadinedu.common.constant.BPermissionConstant;
import xyz.eleadinedu.common.constant.BusinessTypeConstant;
import xyz.eleadinedu.common.exception.NotFoundException;
import xyz.eleadinedu.common.service.UserService;
import xyz.eleadinedu.common.types.JsonResponse;
import xyz.eleadinedu.exam.ExamPaperService;
import xyz.eleadinedu.exam.ExamPaperTypes.IdInput;
import xyz.eleadinedu.exam.ExamPaperTypes.StudentRecordQuery;

/** Management-side access to one learner's immutable exam records. */
@RestController
@RequestMapping("/backend/v1/user/{userId}/exam-records")
public class UserExamRecordController {
    private final UserService users;
    private final ExamPaperService exams;

    public UserExamRecordController(UserService users, ExamPaperService exams) {
        this.users = users;
        this.exams = exams;
    }

    @PostMapping
    @BackendPermission(slug = BPermissionConstant.USER_LEARN)
    @Log(title = "学员-考试记录", businessType = BusinessTypeConstant.GET)
    public JsonResponse records(
            @PathVariable Integer userId, @Valid @RequestBody StudentRecordQuery input)
            throws NotFoundException {
        users.findOrFail(userId);
        return JsonResponse.data(exams.studentRecords(input, userId));
    }

    @PostMapping("/detail")
    @BackendPermission(slug = BPermissionConstant.USER_LEARN)
    @Log(title = "学员-考试答题明细", businessType = BusinessTypeConstant.GET)
    public JsonResponse detail(@PathVariable Integer userId, @Valid @RequestBody IdInput input)
            throws NotFoundException {
        users.findOrFail(userId);
        return JsonResponse.data(exams.studentRecordDetail(input.id(), userId));
    }
}
