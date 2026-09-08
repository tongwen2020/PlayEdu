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
package xyz.playedu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP routing contract for every REST controller endpoint.
 *
 * <p>The cases are discovered from Spring mapping annotations instead of a hand-maintained list.
 * Adding a controller or endpoint therefore adds a test case automatically. Dependencies are mocked
 * because this suite verifies the HTTP contract (verb, path and selected handler); domain behavior
 * belongs in focused controller or integration tests.
 */
class ControllerRouteContractTest {
    private static final String CONTROLLER_PACKAGE = "xyz.playedu.api.controller";
    private static final HandlerCapture CAPTURE = new HandlerCapture();

    private static List<Class<?>> controllerTypes;
    private static List<Endpoint> endpoints;
    private static MockMvc mvc;

    @BeforeAll
    static void setUpRoutes() throws Exception {
        controllerTypes = discoverControllers();
        Object[] controllers = new Object[controllerTypes.size()];
        for (int i = 0; i < controllerTypes.size(); i++) {
            controllers[i] = controller(controllerTypes.get(i));
        }
        mvc = MockMvcBuilders.standaloneSetup(controllers).addInterceptors(CAPTURE).build();
        endpoints = discoverEndpoints(controllerTypes);
    }

    @Test
    void everyRestControllerDeclaresAtLeastOneEndpoint() {
        assertThat(controllerTypes).isNotEmpty();
        for (Class<?> controllerType : controllerTypes) {
            assertThat(endpoints)
                    .as("REST controller %s must expose an endpoint", controllerType.getName())
                    .anyMatch(endpoint -> endpoint.controllerType().equals(controllerType));
        }
    }

    @Test
    void endpointDisplayNamesAreUnique() {
        Set<String> names = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            assertThat(names.add(endpoint.displayName()))
                    .as("duplicate endpoint contract: %s", endpoint.displayName())
                    .isTrue();
        }
    }

    @TestFactory
    Stream<DynamicTest> everyEndpointRoutesToItsDeclaredControllerMethod() {
        return endpoints.stream()
                .map(
                        endpoint ->
                                DynamicTest.dynamicTest(
                                        endpoint.displayName(), () -> assertRoute(endpoint)));
    }

    private static void assertRoute(Endpoint endpoint) {
        CAPTURE.clear();
        try {
            mvc.perform(request(endpoint)).andReturn();
        } catch (Exception ignored) {
            // A mocked domain dependency may make the handler fail. Routing has already happened.
        }

        HandlerMethod actual = CAPTURE.handler();
        assertThat(actual).as("Spring did not resolve %s", endpoint.displayName()).isNotNull();
        assertThat(actual.getBeanType()).isEqualTo(endpoint.controllerType());
        assertThat(actual.getMethod()).isEqualTo(endpoint.method());
    }

    private static MockHttpServletRequestBuilder request(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.request(endpoint.httpMethod(), endpoint.path())
                        .contentType(
                                endpoint.consumes().length == 0
                                        ? MediaType.APPLICATION_JSON
                                        : MediaType.parseMediaType(endpoint.consumes()[0]))
                        .content("{}");
        if (endpoint.produces().length > 0) {
            builder.accept(MediaType.parseMediaType(endpoint.produces()[0]));
        }
        for (String condition : endpoint.params()) {
            applyParameterCondition(builder, condition);
        }
        for (String condition : endpoint.headers()) {
            applyHeaderCondition(builder, condition);
        }
        return builder;
    }

    private static void applyParameterCondition(
            MockHttpServletRequestBuilder builder, String condition) {
        if (condition.startsWith("!")) {
            return;
        }
        int notEquals = condition.indexOf("!=");
        if (notEquals > 0) {
            builder.param(condition.substring(0, notEquals), "contract-test-value");
            return;
        }
        int equals = condition.indexOf('=');
        if (equals > 0) {
            builder.param(condition.substring(0, equals), condition.substring(equals + 1));
        } else {
            builder.param(condition, "contract-test-value");
        }
    }

    private static void applyHeaderCondition(
            MockHttpServletRequestBuilder builder, String condition) {
        if (condition.startsWith("!")) {
            return;
        }
        int notEquals = condition.indexOf("!=");
        if (notEquals > 0) {
            builder.header(condition.substring(0, notEquals), "contract-test-value");
            return;
        }
        int equals = condition.indexOf('=');
        if (equals > 0) {
            builder.header(condition.substring(0, equals), condition.substring(equals + 1));
        } else {
            builder.header(condition, "contract-test-value");
        }
    }

    private static List<Class<?>> discoverControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> result = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            result.add(Class.forName(candidate.getBeanClassName()));
        }
        result.sort(Comparator.comparing(Class::getName));
        return result;
    }

    private static Object controller(Class<?> type) throws Exception {
        Constructor<?> constructor = BeanUtils.getResolvableConstructor(type);
        Object[] arguments =
                Arrays.stream(constructor.getParameterTypes())
                        .map(ControllerRouteContractTest::dependencyMock)
                        .toArray();
        Object instance = constructor.newInstance(arguments);
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)
                        && !Modifier.isStatic(field.getModifiers())) {
                    ReflectionTestUtils.setField(
                            instance, field.getName(), dependencyMock(field.getType()));
                }
            }
        }
        return instance;
    }

    private static Object dependencyMock(Class<?> type) {
        return mock(type, Mockito.withSettings().stubOnly());
    }

    private static List<Endpoint> discoverEndpoints(List<Class<?>> types) {
        List<Endpoint> result = new ArrayList<>();
        for (Class<?> type : types) {
            RequestMapping controllerMapping =
                    AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] roots = paths(controllerMapping);
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping methodMapping =
                        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (methodMapping == null) {
                    continue;
                }
                RequestMethod[] verbs = methodMapping.method();
                if (verbs.length == 0) {
                    verbs = new RequestMethod[] {RequestMethod.GET};
                }
                for (String root : roots) {
                    for (String leaf : paths(methodMapping)) {
                        for (RequestMethod verb : verbs) {
                            result.add(
                                    new Endpoint(
                                            type,
                                            method,
                                            HttpMethod.valueOf(verb.name()),
                                            concretePath(root, leaf),
                                            methodMapping.params(),
                                            methodMapping.headers(),
                                            methodMapping.consumes(),
                                            methodMapping.produces()));
                        }
                    }
                }
            }
        }
        result.sort(Comparator.comparing(Endpoint::displayName));
        return List.copyOf(result);
    }

    private static String[] paths(RequestMapping mapping) {
        if (mapping == null) {
            return new String[] {""};
        }
        String[] paths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return paths.length == 0 ? new String[] {""} : paths;
    }

    private static String concretePath(String root, String leaf) {
        String joined = ("/" + root + "/" + leaf).replaceAll("/{2,}", "/");
        String path = joined.replaceAll("\\{[^}]+}", "1");
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }

    private record Endpoint(
            Class<?> controllerType,
            Method method,
            HttpMethod httpMethod,
            String path,
            String[] params,
            String[] headers,
            String[] consumes,
            String[] produces) {
        String displayName() {
            return httpMethod
                    + " "
                    + path
                    + " -> "
                    + controllerType.getSimpleName()
                    + "."
                    + method.getName();
        }
    }

    private static final class HandlerCapture implements HandlerInterceptor {
        private final ThreadLocal<HandlerMethod> handler = new ThreadLocal<>();

        @Override
        public boolean preHandle(
                HttpServletRequest request, HttpServletResponse response, Object candidate) {
            if (candidate instanceof HandlerMethod handlerMethod) {
                handler.set(handlerMethod);
            }
            // Handler selection is the contract under test. Stop before mocked business code runs.
            return false;
        }

        HandlerMethod handler() {
            return handler.get();
        }

        void clear() {
            handler.remove();
        }
    }
}
