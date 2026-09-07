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
package xyz.playedu.api.controller.frontend;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.playedu.common.context.FCtx;
import xyz.playedu.common.types.JsonResponse;
import xyz.playedu.exam.QuestionBankService;
import xyz.playedu.exam.QuestionBankTypes.*;

/** Practice is explicitly opt-in per bank; formal examination banks remain private. */
@RestController
@RequestMapping("/api/v1/question-bank")
public class QuestionPracticeController {
    private final QuestionBankService service;

    public QuestionPracticeController(QuestionBankService service) {
        this.service = service;
    }

    @PostMapping("/banks/list")
    public JsonResponse banks() {
        return JsonResponse.data(service.practiceBanks());
    }

    @PostMapping("/questions/list")
    public JsonResponse questions(@Valid @RequestBody Query input) {
        return JsonResponse.data(service.questions(input, true));
    }

    @PostMapping("/questions/detail")
    public JsonResponse detail(@Valid @RequestBody IdInput input) {
        return JsonResponse.data(service.practiceDetail(input.id()));
    }

    @PostMapping("/practice/submit")
    public JsonResponse submit(@Valid @RequestBody PracticeInput input) {
        return JsonResponse.data(service.practice(input, FCtx.getId()));
    }

    @PostMapping("/practice/history")
    public JsonResponse history(@Valid @RequestBody Query input) {
        return JsonResponse.data(service.practiceHistory(FCtx.getId(), input));
    }
}
