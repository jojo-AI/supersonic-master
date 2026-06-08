package com.tencent.supersonic.common.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private Long id;

    private String name;

    private String displayName;

    private String email;

    private Integer isAdmin;

    private Timestamp lastLogin;

    private String tenantId;

    private String apiKey;

    private String datasetKey;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDatasetKey() {
        return datasetKey;
    }

    public void setDatasetKey(String datasetKey) {
        this.datasetKey = datasetKey;
    }

    public static User get(Long id, String name, String displayName, String email, Integer isAdmin,
            String tenantId, String apiKey, String datasetKey) {
        return new User(id, name, displayName, email, isAdmin, null, tenantId, apiKey, datasetKey);
    }

    public static User get(Long id, String name, String tenantId, String apiKey,
            String datasetKey) {
        return new User(id, name, name, name, 0, null, tenantId, apiKey, datasetKey);
    }

    public static User getDefaultUser() {
        return new User(1L, "admin", "admin", "admin@email", 1, null, "admin", "admin", "admin");
    }

    public static User getVisitUser() {
        return new User(1L, "visit", "visit", "visit@email", 0, null, "visit", "visit", "visit");
    }

    public static User getAppUser(int appId) {
        String name = String.format("app_%s", appId);
        return new User(1L, name, name, "", 1, null, name, name, name);
    }

    public String getDisplayName() {
        return StringUtils.isBlank(displayName) ? name : displayName;
    }

    public boolean isSuperAdmin() {
        return isAdmin != null && isAdmin == 1;
    }
}
