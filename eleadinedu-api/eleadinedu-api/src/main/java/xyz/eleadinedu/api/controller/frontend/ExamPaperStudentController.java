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
package xyz.eleadinedu.api.controller.frontend;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.eleadinedu.common.context.FCtx;
import xyz.eleadinedu.common.types.JsonResponse;
import xyz.eleadinedu.exam.ExamPaperService;
import xyz.eleadinedu.exam.ExamPaperTypes.*;

/** Learner-facing published fixed papers and immutable automatic grading. */
@RestController
@RequestMapping("/api/v1/exam-paper")
public class ExamPaperStudentController {
    private final ExamPaperService service;

    public ExamPaperStudentController(ExamPaperService service) {
        this.service = service;
    }

    @PostMapping("/papers/list")
    public JsonResponse papers(@Valid @RequestBody StudentPaperQuery input) {
        return JsonResponse.data(service.studentPapers(input));
    }

    @PostMapping("/papers/detail")
    public JsonResponse detail(@Valid @RequestBody IdInput input) {
        return JsonResponse.data(service.studentDetail(input.id()));
    }

    @PostMapping("/papers/submit")
    public JsonResponse submit(@Valid @RequestBody StudentSubmitInput input) {
        return JsonResponse.data(service.submitStudentPaper(input, FCtx.getId()));
    }
}
