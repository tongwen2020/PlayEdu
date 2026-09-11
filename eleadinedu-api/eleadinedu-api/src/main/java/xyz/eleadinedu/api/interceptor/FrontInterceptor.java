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
package xyz.eleadinedu.api.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import xyz.eleadinedu.common.constant.FrontendConstant;
import xyz.eleadinedu.common.context.FCtx;
import xyz.eleadinedu.common.domain.User;
import xyz.eleadinedu.common.service.FrontendAuthService;
import xyz.eleadinedu.common.service.UserService;
import xyz.eleadinedu.common.types.JsonResponse;
import xyz.eleadinedu.common.util.HelperUtil;

@Component
@Slf4j
@Order(20)
public class FrontInterceptor implements HandlerInterceptor {

    @Autowired private FrontendAuthService authService;

    @Autowired private UserService userService;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (FrontendConstant.UN_AUTH_URI_WHITELIST.contains(request.getRequestURI())) {
            return HandlerInterceptor.super.preHandle(request, response, handler);
        }

        if (!authService.check()) {
            return responseTransform(response, 401, "请登录");
        }

        User user = userService.find(authService.userId());
        if (user == null) {
            return responseTransform(response, 401, "请重新登录");
        }
        if (user.getIsLock() == 1) {
            return responseTransform(response, 403, "当前学员已锁定无法登录");
        }

        FCtx.setUser(user);
        FCtx.setId(user.getId());
        FCtx.setJWtJti(authService.jti());

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    private boolean responseTransform(HttpServletResponse response, int code, String msg)
            throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().print(HelperUtil.toJsonStr(JsonResponse.error(msg)));
        return false;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        FCtx.remove();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
