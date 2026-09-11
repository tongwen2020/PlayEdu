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
package xyz.eleadinedu.common.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import xyz.eleadinedu.common.domain.AdminRolePermission;
import xyz.eleadinedu.common.mapper.AdminRolePermissionMapper;
import xyz.eleadinedu.common.service.AdminRolePermissionService;

/**
 * @author tengteng
 * @description 针对表【admin_role_permission】的数据库操作Service实现
 * @createDate 2023-02-21 16:07:01
 */
@Service
public class AdminRolePermissionServiceImpl
        extends ServiceImpl<AdminRolePermissionMapper, AdminRolePermission>
        implements AdminRolePermissionService {}
