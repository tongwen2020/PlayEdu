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
package xyz.eleadinedu.api.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.eleadinedu.api.event.UserDestroyEvent;
import xyz.eleadinedu.common.service.UserLoginRecordService;
import xyz.eleadinedu.common.service.UserService;
import xyz.eleadinedu.course.service.UserCourseHourRecordService;
import xyz.eleadinedu.course.service.UserCourseRecordService;
import xyz.eleadinedu.course.service.UserLearnDurationRecordService;
import xyz.eleadinedu.course.service.UserLearnDurationStatsService;

/**
 * @Author 杭州白书科技有限公司
 *
 * @create 2023/2/23 15:18
 */
@Component
@Slf4j
public class UserDestroyListener {

    @Autowired private UserService userService;

    @Autowired private UserCourseHourRecordService userCourseHourRecordService;

    @Autowired private UserCourseRecordService userCourseRecordService;

    @Autowired private UserLearnDurationRecordService userLearnDurationRecordService;

    @Autowired private UserLearnDurationStatsService userLearnDurationStatsService;

    @Autowired private UserLoginRecordService userLoginRecordService;

    @EventListener
    public void remoteRelation(UserDestroyEvent event) {
        userService.removeRelateDepartmentsByUserId(event.getUserId());
        userCourseHourRecordService.remove(event.getUserId());
        userCourseRecordService.destroy(event.getUserId());
        userLearnDurationRecordService.remove(event.getUserId());
        userLearnDurationStatsService.remove(event.getUserId());
        userLoginRecordService.remove(event.getUserId());
    }
}
