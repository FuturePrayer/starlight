package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 笔记分类实体。
 * <p>支持树形结构，通过 parent 字段实现父子分类关系。</p>
 *
 * @author suhoan
 */
@Entity
@Table(name = "sl_category")
public class Category extends BaseEntity {

    /** 分类所属用户，懒加载 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    /** 父分类，为 null 表示顶级分类，懒加载 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /** 分类名称 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 排序序号，值越小越靠前 */
    @Column(nullable = false)
    private int sortOrder = 0;

    /** 公开站点（星迹书阁）访问令牌，非空表示该分类已开启星迹书阁 */
    @Column(length = 64, unique = true)
    private String siteToken;

    /** 站点自定义标题，为空时使用分类名称 */
    @Column(length = 200)
    private String siteTitle;

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public Category getParent() {
        return parent;
    }

    public void setParent(Category parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getSiteToken() {
        return siteToken;
    }

    public void setSiteToken(String siteToken) {
        this.siteToken = siteToken;
    }

    public String getSiteTitle() {
        return siteTitle;
    }

    public void setSiteTitle(String siteTitle) {
        this.siteTitle = siteTitle;
    }
}

