package com.example.freezeappdemo1.entity;

import android.graphics.drawable.Drawable;

import androidx.lifecycle.LiveData;

public class AppInfo {
    private String appName;
    private String packageName;
    private Drawable icon;
    private boolean isHidden;
    private boolean isSystemApp;

    private boolean isSelectedReadyToFreeze;

    @Override
    public String toString() {
        return "AppInfo{" +
                "appName='" + appName + '\'' +
                ", isSelectedReadyToFreeze=" + isSelectedReadyToFreeze +
                '}';
    }

    public AppInfo() {
    }

    public boolean isSelected() {
        return isSelectedReadyToFreeze;
    }

    public void setSelected(boolean selectedReadyToFreeze) {
        isSelectedReadyToFreeze = selectedReadyToFreeze;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isSystemApp() {
        return isSystemApp;
    }

    public void setSystemApp(boolean systemApp) {
        isSystemApp = systemApp;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public void setHidden(boolean hidden) {
        isHidden = hidden;
    }

    public AppInfo(String appName, String packageName, Drawable icon, boolean isHidden, boolean isSystemApp, boolean isSelectedReadyToFreeze) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isHidden = isHidden;
        this.isSystemApp = isSystemApp;
        this.isSelectedReadyToFreeze = isSelectedReadyToFreeze;
    }
}