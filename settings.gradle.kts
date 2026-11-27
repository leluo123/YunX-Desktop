// 仓库源开关：默认优先国内阿里云镜像（本机常无法直连 dl.google.com / repo1）；
// CI（GitHub Runner，海外网络）设 YUNX_USE_MIRROR=false 走官方源 —— 阿里云镜像对部分
// Compose 构件（如 org.jetbrains.compose.desktop:*-windows-x64）会返回 502，导致构建失败。
pluginManagement {
    repositories {
        if (System.getenv("YUNX_USE_MIRROR")?.lowercase() != "false") {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        if (System.getenv("YUNX_USE_MIRROR")?.lowercase() != "false") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "YunX-Desktop"
include(":desktop")
