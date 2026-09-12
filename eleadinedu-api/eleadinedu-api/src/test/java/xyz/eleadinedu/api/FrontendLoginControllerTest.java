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
package xyz.eleadinedu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import xyz.eleadinedu.api.bus.LoginBus;
import xyz.eleadinedu.api.cache.LoginLimitCache;
import xyz.eleadinedu.api.cache.LoginLockCache;
import xyz.eleadinedu.api.controller.frontend.LoginController;
import xyz.eleadinedu.api.request.frontend.LoginPasswordRequest;
import xyz.eleadinedu.common.domain.User;
import xyz.eleadinedu.common.service.AppConfigService;
import xyz.eleadinedu.common.service.FrontendAuthService;
import xyz.eleadinedu.common.service.UserService;
import xyz.eleadinedu.common.types.JsonResponse;
import xyz.eleadinedu.common.util.HelperUtil;

@ExtendWith(MockitoExtension.class)
class FrontendLoginControllerTest {
    @Mock private UserService userService;
    @Mock private FrontendAuthService authService;
    @Mock private ApplicationContext applicationContext;
    @Mock private AppConfigService appConfigService;
    @Mock private LoginBus loginBus;
    @Mock private LoginLimitCache loginLimitCache;
    @Mock private LoginLockCache loginLockCache;

    @InjectMocks private LoginController controller;

    @Test
    void localPasswordLoginAcceptsAccountAndReturnsToken() throws Exception {
        LoginPasswordRequest request = new LoginPasswordRequest();
        request.setAccount(" student001 ");
        request.setPassword("secret");
        User user = new User();
        user.setId(7);
        user.setIsLock(0);
        user.setSalt("salt");
        user.setPassword(HelperUtil.MD5("secretsalt"));
        when(userService.findByLoginIdentifier("student001")).thenReturn(user);
        when(loginBus.tokenByUser(user)).thenReturn(new HashMap<>(Map.of("token", "test-token")));

        JsonResponse response = controller.password(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isEqualTo(Map.of("token", "test-token"));
        verify(loginLimitCache).check("student001");
        verify(loginLimitCache).destroy("student001");
    }

    @Test
    void legacyEmailRequestFieldRemainsCompatible() throws Exception {
        LoginPasswordRequest request =
                new ObjectMapper()
                        .readValue(
                                "{\"email\":\"old@example.com\",\"password\":\"secret\"}",
                                LoginPasswordRequest.class);

        assertThat(request.getAccount()).isEqualTo("old@example.com");
    }
}
