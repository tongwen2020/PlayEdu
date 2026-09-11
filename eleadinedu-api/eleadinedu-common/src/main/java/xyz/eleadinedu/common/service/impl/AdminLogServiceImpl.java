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
import xyz.eleadinedu.common.domain.AdminLog;
import xyz.eleadinedu.common.mapper.AdminLogMapper;
import xyz.eleadinedu.common.service.AdminLogService;
import xyz.eleadinedu.common.types.paginate.AdminLogPaginateFiler;
import xyz.eleadinedu.common.types.paginate.PaginationResult;

/**
 * @author tengteng
 * @description 针对表【admin_logs】的数据库操作Service实现
 * @createDate 2023-02-17 15:40:31
 */
@Service
public class AdminLogServiceImpl extends ServiceImpl<AdminLogMapper, AdminLog>
        implements AdminLogService {
    @Override
    public PaginationResult<AdminLog> paginate(int page, int size, AdminLogPaginateFiler filter) {
        filter.setPageStart((page - 1) * size);
        filter.setPageSize(size);

        PaginationResult<AdminLog> pageResult = new PaginationResult<>();
        pageResult.setData(getBaseMapper().paginate(filter));
        pageResult.setTotal(getBaseMapper().paginateCount(filter));

        return pageResult;
    }

    @Override
    public AdminLog find(Integer id, Integer adminId) {
        if (adminId == 0) {
            return getOne(query().getWrapper().eq("id", id));
        }
        return getOne(query().getWrapper().eq("id", id).eq("admin_id", adminId));
    }
}
