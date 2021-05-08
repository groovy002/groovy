/*
 * Copyright (c) 2021 Henry 李恒 (henry.box@outlook.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pub.ihub.plugin

import static pub.ihub.plugin.Constants.GROOVY_VERSION
import static pub.ihub.plugin.IHubPluginMethods.dependenciesTap
import static pub.ihub.plugin.IHubPluginMethods.dependencyTypeTap
import static pub.ihub.plugin.IHubPluginMethods.findProperty
import static pub.ihub.plugin.IHubPluginMethods.groupTap
import static pub.ihub.plugin.IHubPluginMethods.moduleTap
import static pub.ihub.plugin.IHubPluginMethods.printConfigContent
import static pub.ihub.plugin.IHubPluginMethods.versionTap

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * BOM（Bill of Materials）组件依赖管理
 * @author henry
 */
class IHubBomPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		project.pluginManager.apply IHubPluginsPlugin

		project.pluginManager.apply 'io.spring.dependency-management'

		project.afterEvaluate({ IHubBomExtension ext ->
			if (findProperty(project, 'enabledBomDefaultConfig', ext.enabledDefaultConfig.toString()).toBoolean()) {
				// 配置导入bom
				ext.importBoms {
					allIfAbsent = true
					// TODO 由于GitHub仓库token只能个人使用，组件发布到中央仓库方可使用
//					group 'pub.ihub.lib' module 'ihub-libs' version '1.0.0-SNAPSHOT'
					group 'org.codehaus.groovy' module 'groovy-bom' version GROOVY_VERSION
					group 'org.spockframework' module 'spock-bom' version '2.0-M4-groovy-3.0'
					group 'org.springframework.boot' module 'spring-boot-dependencies' version '2.4.5'
					group 'org.springframework.cloud' module 'spring-cloud-dependencies' version '2020.0.2'
					group 'com.alibaba.cloud' module 'spring-cloud-alibaba-dependencies' version '2021.1'
					group 'com.github.xiaoymin' module 'knife4j-dependencies' version '3.0.2'
					group 'com.sun.xml.bind' module 'jaxb-bom-ext' version '3.0.1'
					group 'de.codecentric' module 'spring-boot-admin-dependencies' version '2.4.1'
				}
				// 配置组件依赖版本
				ext.dependencyVersions {
					group 'org.codehaus.groovy' version GROOVY_VERSION modules 'groovy-all'
					group 'com.alibaba' version '1.2.76' modules 'fastjson'
					group 'com.alibaba' version '1.2.6' modules 'druid', 'druid-spring-boot-starter'
					group 'com.alibaba.p3c' version '2.1.1' modules 'p3c-pmd'
					group 'com.baomidou' version '3.4.2' modules 'mybatis-plus',
						'mybatis-plus-boot-starter', 'mybatis-plus-generator'
					group 'com.github.xiaoymin' version '2.0.8' modules 'knife4j-aggregation-spring-boot-starter'
					group 'com.athaydes' version '2.0.1-RC3' modules 'spock-reports'
				}
				// 配置组版本策略（建议尽量使用bom）
				ext.groupVersions {
					group 'cn.hutool' version '5.6.4' ifAbsent true
				}
				// 配置默认排除项
				ext.excludeModules {
					group 'c3p0' modules 'c3p0'
					group 'commons-logging' modules 'commons-logging'
					group 'com.zaxxer' modules 'HikariCP'
					group 'log4j' modules 'log4j'
					group 'org.apache.logging.log4j' modules 'log4j-core'
					group 'org.apache.tomcat' modules 'tomcat-jdbc'
					group 'org.slf4j' modules 'slf4j-jcl', 'slf4j-log4j12'
					group 'stax' modules 'stax-api'
				}
				// 配置默认依赖组件
				ext.dependencies {
					compileOnly 'cn.hutool:hutool-all'
					implementation 'org.slf4j:slf4j-api'
					runtimeOnly 'org.slf4j:jul-to-slf4j',
//						'org.slf4j:jcl-over-slf4j', TODO 构建原生镜像有报错
						'org.slf4j:log4j-over-slf4j'
					project.extensions.findByType(IHubGroovyExtension)?.with {
						implementation modules.unique().collect { "org.codehaus.groovy:$it" } as String[]
					}
				}
			}


			project.dependencyManagement {
				// 导入bom配置
				imports {
					ext.bomVersions.each {
						mavenBom "$it.group:$it.module:${findVersion(project, it.group, it.version)}"
					}
				}

				// 配置组件版本
				dependencies {
					ext.dependencyVersions.each { config ->
						dependencySet(group: config.group, version: findVersion(project, config.group, config.version)) {
							config.modules.each { entry it }
						}
					}
				}
			}

			project.configurations {
				all {
					resolutionStrategy {
						// 配置组件组版本（用于配置无bom组件）
						eachDependency {
							String group = it.requested.group
							findVersion(project, group, ext.groupVersions[group])?.with { version ->
								it.useVersion version
							}
						}
						// 不缓存动态版本
						cacheDynamicVersionsFor 0, 'seconds'
						// 不缓存快照模块
						cacheChangingModulesFor 0, 'seconds'
					}
					// 排除组件依赖
					ext.excludeModules.each { group, modules ->
						modules.each { module -> exclude group: group, module: module }
					}
				}
				// 配置组件依赖
				ext.dependencies.each { type, dependencies ->
					maybeCreate(type).dependencies.addAll dependencies.collect {
						// 支持导入项目
						project.dependencies.create it.startsWith(':') ? project.project(it) : it
					}
				}

				if (findProperty(project, 'printBomConfig', ext.printConfig.toString()).toBoolean()) {
					printConfigContent "${project.name.toUpperCase()} Group Maven Module Version",
						ext.dependencyVersions.inject([]) { list, config ->
							list + config.modules.collect { [config.group, it, config.version] }
						}, groupTap(35), moduleTap(), versionTap(15)
					printConfigContent "${project.name.toUpperCase()} Group Maven Bom Version", ext.bomVersions.collect {
						[it.group, it.module, it.version]
					}, groupTap(30), moduleTap(), versionTap(20)
					printConfigContent "${project.name.toUpperCase()} Group Maven Default Version",
						groupTap(), versionTap(), ext.groupVersions
					printConfigContent "${project.name.toUpperCase()} Exclude Group Modules",
						groupTap(40), moduleTap(), ext.excludeModules
					printConfigContent "${project.name.toUpperCase()} Config Default Dependencies",
						dependencyTypeTap(), dependenciesTap(), ext.dependencies
				}
			}
		}.curry(project.extensions.create('iHubBom', IHubBomExtension)))
	}

	private static String findVersion(Project project, String group, String defaultVersion) {
		findProperty project, group + '.version', defaultVersion
	}

}
