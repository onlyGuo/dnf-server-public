package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.Sort;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.enums.OrderBy;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.sql.where.C;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.ClientLauncherBannerDao;
import com.aiyi.game.dnfserver.dao.ClientLauncherConfigDao;
import com.aiyi.game.dnfserver.dao.ClientLauncherVersionDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.ClientLauncherBanner;
import com.aiyi.game.dnfserver.entity.ClientLauncherConfig;
import com.aiyi.game.dnfserver.entity.ClientLauncherVersion;
import com.aiyi.game.dnfserver.service.ClientLauncherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 登录器管理业务实现
 * @author gsk
 */
@Service
public class ClientLauncherServiceImpl implements ClientLauncherService {

    @Resource
    private ClientLauncherConfigDao configDao;

    @Resource
    private ClientLauncherVersionDao versionDao;

    @Resource
    private ClientLauncherBannerDao bannerDao;

    @Resource
    private AccountVODao accountVODao;

    @Override
    public ClientLauncherConfig getConfig() {
        // 按约定只保留 1 行配置（id=1）
        ClientLauncherConfig cfg = configDao.get(1);
        if (cfg == null) {
            cfg = new ClientLauncherConfig();
            cfg.setId(1);
            cfg.setVersion("1.0.0");
            cfg.setDownloadUrl("");
            cfg.setMd5("");
            cfg.setRemark("");
            cfg.setUpdateTime(new Date());
            configDao.add(cfg);
        }
        return cfg;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientLauncherConfig saveConfig(ClientLauncherConfig config) {
        if (config == null) {
            throw new ValidationException("参数不能为空");
        }
        if (config.getId() == 0) {
            config.setId(1);
        }
        if (!StringUtils.hasText(config.getVersion())) {
            throw new ValidationException("版本号不能为空");
        }

        config.setUpdateTime(new Date());
        ClientLauncherConfig exists = configDao.get(config.getId());
        if (exists == null) {
            configDao.add(config);
        } else {
            configDao.update(config);
        }
        return configDao.get(config.getId());
    }

    @Override
    public ResultPage<ClientLauncherVersion> listVersions(String keyword, Integer enabled, int page, int pageSize) {
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }

        WherePrams where = Method.createDefault();
        if (StringUtils.hasText(keyword)) {
            where.and(ClientLauncherVersion::getVersion, C.LIKE, keyword.trim());
        }
        if (enabled != null) {
            where.and(ClientLauncherVersion::getEnabled, C.EQ, enabled);
        }

        where.orderBy(Sort.of(ClientLauncherVersion::getCreateTime, OrderBy.DESC));
        return versionDao.list(where, page, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientLauncherVersion addVersion(ClientLauncherVersion version) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isSuperAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        if (version == null) {
            throw new ValidationException("参数不能为空");
        }
        if (!StringUtils.hasText(version.getVersion())) {
            throw new ValidationException("版本号不能为空");
        }
        if (!StringUtils.hasText(version.getDownloadUrl())) {
            throw new ValidationException("下载地址不能为空");
        }

        if (version.getEnabled() == null) {
            version.setEnabled(1);
        }
        if (version.getForceUpdate() == null) {
            version.setForceUpdate(0);
        }
        version.setCreateTime(new Date());
        versionDao.add(version);
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientLauncherVersion updateVersion(ClientLauncherVersion version) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isSuperAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        if (version == null) {
            throw new ValidationException("参数不能为空");
        }
        if (version.getId() <= 0) {
            throw new ValidationException("ID不能为空");
        }

        ClientLauncherVersion exists = versionDao.get(version.getId());
        if (exists == null) {
            throw new ValidationException("记录不存在");
        }

        if (version.getCreateTime() == null) {
            version.setCreateTime(exists.getCreateTime());
        }
        versionDao.update(version);
        return versionDao.get(version.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(int id) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isSuperAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        if (id <= 0) {
            return;
        }
        versionDao.execute("DELETE FROM `dnf_service`.`CLIENT_LAUNCHER_VERSION` WHERE ID = ?", id);
    }

    @Override
    public ClientLauncherVersion getLatestEnabledVersion() {
        WherePrams where = Method.createDefault();
        where.and(ClientLauncherVersion::getEnabled, C.EQ, 1);
        where.orderBy(Sort.of(ClientLauncherVersion::getCreateTime, OrderBy.DESC));
        ResultPage<ClientLauncherVersion> page = versionDao.list(where, 1, 1);
        return page.getList().isEmpty() ? null : page.getList().get(0);
    }

    @Override
    public List<ClientLauncherBanner> listEnabledBanners() {
        WherePrams where = Method.createDefault();
        where.and(ClientLauncherBanner::getEnabled, C.EQ, 1);
        where.orderBy(Sort.of(ClientLauncherBanner::getSortNo, OrderBy.ASC));
        where.orderBy(Sort.of(ClientLauncherBanner::getId, OrderBy.ASC));
        return bannerDao.list(where);
    }

    @Override
    public List<ClientLauncherBanner> listBanners(Integer enabled) {
        WherePrams where = Method.createDefault();
        if (enabled != null) {
            where.and(ClientLauncherBanner::getEnabled, C.EQ, enabled);
        }
        where.orderBy(Sort.of(ClientLauncherBanner::getSortNo, OrderBy.ASC));
        where.orderBy(Sort.of(ClientLauncherBanner::getId, OrderBy.ASC));
        return bannerDao.list(where);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientLauncherBanner addBanner(ClientLauncherBanner banner) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isSuperAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        if (banner == null) {
            throw new ValidationException("参数不能为空");
        }
        if (!StringUtils.hasText(banner.getTitle())) {
            throw new ValidationException("标题不能为空");
        }
        if (!StringUtils.hasText(banner.getImageUrl())) {
            throw new ValidationException("图片地址不能为空");
        }
        if (banner.getEnabled() == null) {
            banner.setEnabled(1);
        }
        if (banner.getSortNo() == null) {
            banner.setSortNo(0);
        }
        banner.setCreateTime(new Date());
        bannerDao.add(banner);
        return banner;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientLauncherBanner updateBanner(ClientLauncherBanner banner) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isSuperAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        if (banner == null) {
            throw new ValidationException("参数不能为空");
        }
        if (banner.getId() <= 0) {
            throw new ValidationException("ID不能为空");
        }
        ClientLauncherBanner exists = bannerDao.get(banner.getId());
        if (exists == null) {
            throw new ValidationException("记录不存在");
        }
        if (banner.getCreateTime() == null) {
            banner.setCreateTime(exists.getCreateTime());
        }
        bannerDao.update(banner);
        return bannerDao.get(banner.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBanner(int id) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isSuperAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        if (id <= 0) {
            return;
        }
        bannerDao.execute("DELETE FROM `dnf_service`.`CLIENT_LAUNCHER_BANNER` WHERE ID = ?", id);
    }

    @Override
    public List<ClientLauncherVersion> listUpdateVersions(String clientVersion) {
        WherePrams where = Method.createDefault();
        where.and(ClientLauncherVersion::getEnabled, C.EQ, 1);
        where.and(ClientLauncherVersion::getVersion, C.DA, clientVersion);
        where.orderBy(Sort.of(ClientLauncherVersion::getCreateTime, OrderBy.DESC));
        return versionDao.list(where);
    }
}
