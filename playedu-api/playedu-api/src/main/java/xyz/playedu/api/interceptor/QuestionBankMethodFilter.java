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
package xyz.playedu.api.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.playedu.common.types.JsonResponse;

/** Enforce POST even when Spring cannot resolve a handler for the wrong method. */
@Component
@Order(-100)
public class QuestionBankMethodFilter extends OncePerRequestFilter {
    private final ObjectMapper json;

    public QuestionBankMethodFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if ((path.startsWith("/backend/v1/question-bank/")
                        || path.startsWith("/api/v1/question-bank/"))
                && !request.getMethod().equals("POST")
                && !request.getMethod().equals("OPTIONS")) {
            response.setStatus(405);
            response.setHeader("Allow", "POST");
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getWriter(), JsonResponse.error("题库接口仅支持 POST", 405));
            return;
        }
        chain.doFilter(request, response);
    }
}
