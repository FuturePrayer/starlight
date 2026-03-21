package cn.suhoan.startlight.repository;

import cn.suhoan.startlight.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}

