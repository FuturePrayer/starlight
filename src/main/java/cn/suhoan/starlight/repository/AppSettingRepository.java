package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 应用配置数据访问层。
 * <p>以键值对形式（key 为主键）存取系统配置项。</p>
 *
 * @author suhoan
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}

