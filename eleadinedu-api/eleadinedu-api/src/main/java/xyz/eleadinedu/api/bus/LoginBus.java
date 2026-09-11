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
package xyz.eleadinedu.api.bus;

import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.eleadinedu.api.event.UserLoginEvent;
import xyz.eleadinedu.common.bus.LDAPBus;
import xyz.eleadinedu.common.domain.User;
import xyz.eleadinedu.common.exception.ServiceException;
import xyz.eleadinedu.common.service.*;
import xyz.eleadinedu.common.util.IpUtil;
import xyz.eleadinedu.common.util.RequestUtil;
import xyz.eleadinedu.common.util.ldap.LdapTransformUser;

@Component
@Slf4j
public class LoginBus {

    @Autowired private FrontendAuthService authService;

    @Autowired private AppConfigService appConfigService;

    @Autowired private LDAPBus ldapBus;

    @Autowired private ApplicationContext ctx;

    public HashMap<String, Object> tokenByUser(User user) {
        String token = authService.loginUsingId(user.getId(), RequestUtil.url());

        HashMap<String, Object> data = new HashMap<>();
        data.put("token", token);

        ctx.publishEvent(
                new UserLoginEvent(
                        this,
                        user.getId(),
                        user.getEmail(),
                        token,
                        IpUtil.getIpAddress(),
                        RequestUtil.ua()));

        return data;
    }

    @Transactional
    public HashMap<String, Object> tokenByLdapTransformUser(LdapTransformUser ldapTransformUser)
            throws ServiceException {
        User user = ldapBus.singleUserSync(ldapTransformUser, appConfigService.defaultAvatar());
        if (user == null) {
            throw new ServiceException("用户状态异常，无法登录！");
        }
        return tokenByUser(user);
    }
}
