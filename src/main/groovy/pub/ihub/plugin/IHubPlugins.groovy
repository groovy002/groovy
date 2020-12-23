package pub.ihub.plugin


import org.gradle.api.Plugin
import org.gradle.api.Project

import static pub.ihub.plugin.Constants.GROUP_DEFAULT_DEPENDENCIES_MAPPING
import static pub.ihub.plugin.Constants.GROUP_DEPENDENCY_EXCLUDE_MAPPING
import static pub.ihub.plugin.Constants.GROUP_DEPENDENCY_VERSION_MAPPING
import static pub.ihub.plugin.Constants.MAVEN_CENTRAL_REPOSITORY
import static pub.ihub.plugin.Constants.MAVEN_LOCAL_ENABLED
import static pub.ihub.plugin.PluginUtils.findProperty



/**
 * @author liheng
 */
class IHubPlugins implements Plugin<Project> {

    private static final Closure REPOSITORIES_CONFIGURE = { Project project ->
        flatDir dirs: "$project.rootProject.projectDir/libs"
        if (findProperty(MAVEN_LOCAL_ENABLED, 'false').toBoolean()) {
            mavenLocal()
        }
        // TODO 添加私有仓库
        // 添加自定义仓库
        def mavenCentralRepo = findProperty project, MAVEN_CENTRAL_REPOSITORY
        if (mavenCentralRepo && !mavenCentralRepo.blank) {
            maven { url mavenCentralRepo }
        }
        maven { url 'https://maven.aliyun.com/repository/public/' }
        maven { url 'https://maven.aliyun.com/nexus/content/groups/public/' }
        mavenCentral()
        jcenter()
    }

    private static final Closure CONFIGURATIONS_CONFIGURE = { Project project ->
        all {
            resolutionStrategy {
                eachDependency {
                    def findner = GROUP_DEPENDENCY_VERSION_MAPPING[it.requested.group]
                    if (findner) {
                        it.useVersion findner(project)
                    }
                }
                // 不缓存动态版本
                cacheDynamicVersionsFor 0, 'seconds'
                // 不缓存快照模块
                cacheChangingModulesFor 0, 'seconds'
            }
        }
        all {
            GROUP_DEPENDENCY_EXCLUDE_MAPPING.each { group, modules ->
                modules.each { module ->
                    exclude group: group, module: module
                }
            }
        }
        GROUP_DEFAULT_DEPENDENCIES_MAPPING.each { key, dependencies ->
            maybeCreate(key).getDependencies()
                    .addAll(dependencies.collect { project.getDependencies().create(it) })
        }
    }

    @Override
    void apply(Project project) {
        project.repositories REPOSITORIES_CONFIGURE.curry(project)
        project.subprojects { repositories REPOSITORIES_CONFIGURE.curry(project) }
        println '<<<<<<<<<<<<<<<<<<<<<<<<<<配置组件仓库>>>>>>>>>>>>>>>>>>>>>>>>>>'
        println project.repositories*.displayName.join('\n')

        project.configurations CONFIGURATIONS_CONFIGURE.curry(project)
        project.subprojects { configurations CONFIGURATIONS_CONFIGURE.curry(project) }
        println '<<<<<<<<<<<<<<<<<<<<<<<<配置组件默认版本>>>>>>>>>>>>>>>>>>>>>>>>'
        GROUP_DEPENDENCY_VERSION_MAPPING.each { key, findner ->
            println "${key} -> ${findner(project)}"
        }
        println '<<<<<<<<<<<<<<<<<<<<<<<<排除组件默认依赖>>>>>>>>>>>>>>>>>>>>>>>>'
        GROUP_DEPENDENCY_EXCLUDE_MAPPING.each { key, modules ->
            modules.each { println "${key} -> $it" }
        }
        println '<<<<<<<<<<<<<<<<<<<<<<<<配置默认依赖组件>>>>>>>>>>>>>>>>>>>>>>>>'
        GROUP_DEFAULT_DEPENDENCIES_MAPPING.each { key, list ->
            list.each { println "$key $it" }
        }
    }

}
