-- 为分类增加公开站点（星迹书阁）能力
-- site_token: 公开访问令牌，非空表示该分类已开启星迹书阁
-- site_title: 站点自定义标题，为空时使用分类名

ALTER TABLE sl_category ADD COLUMN site_token VARCHAR(64) NULL;
ALTER TABLE sl_category ADD COLUMN site_title VARCHAR(200) NULL;

CREATE UNIQUE INDEX uk_sl_category_site_token ON sl_category(site_token);

