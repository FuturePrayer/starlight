package cn.suhoan.starlight.repository;

import cn.suhoan.starlight.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}

