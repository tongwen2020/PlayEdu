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
package xyz.eleadinedu.system.aspectj;

import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.eleadinedu.common.annotation.BackendPermission;
import xyz.eleadinedu.common.bus.BackendBus;
import xyz.eleadinedu.common.context.BCtx;
import xyz.eleadinedu.common.types.JsonResponse;

@Aspect
@Component
@Slf4j
public class BackendPermissionAspect {

    @Autowired private BackendBus backendBus;

    @Pointcut("@annotation(xyz.eleadinedu.common.annotation.BackendPermission)")
    private void doPointcut() {}

    @Around("doPointcut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        BackendPermission middleware = signature.getMethod().getAnnotation(BackendPermission.class);
        Integer adminUserId = BCtx.getId();
        HashMap<String, Boolean> permissions = backendBus.adminUserPermissions(adminUserId);
        if (permissions.get(middleware.slug()) == null) {
            return JsonResponse.error("权限不足", 403);
        }
        return joinPoint.proceed();
    }
}
